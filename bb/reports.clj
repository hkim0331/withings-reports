(ns reports
  (:require
   [babashka.curl :as curl]
   [babashka.pods :as pods]
   [cheshire.core :as json]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as str]
   [clojure.tools.logging :as log]))

(pods/load-pod 'org.babashka/mysql "0.1.1")
(require '[pod.babashka.mysql :as mysql])
(def db {:dbtype   "mysql"
         :host     "localhost"
         :port     3306
         :dbname   "withings"
         :user     (System/getenv "MYSQL_USER")
         :password (System/getenv "MYSQL_PASSWORD")})

(def wc (System/getenv "WC"))
(def lp (System/getenv "LP"))
(def admin    (System/getenv "WC_LOGIN"))
(def password (System/getenv "WC_PASSWORD"))

(def cookie "reports.txt")

(def users (atom nil))
(def measures (atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; utils
(defn curl-get [url & params]
  (curl/get url {:raw-args (vec (concat ["-b" cookie] params))}))

;; push line-push only
;; (defn curl-post [url & params]
;;   (curl/post url {:raw-args (vec (concat ["-b" cookie] params))}))

(defn today []
  (-> (sh "date" "+%F")
      :out
      str/trim-newline))

(defn now []
  (-> (sh "date" "+%F %T")
      :out
      str/trim-newline))

(comment
  (today)
  (now)
  :rcf)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; login, users, measures
(defn login
  "login. if success, updates cookie and returns 302."
  []
  (let [api (str wc "/")
        params (str "login=" admin "&password=" password)]
    (curl/post api {:raw-args ["-c" cookie "-d" params]
                    :follow-redirects false})))

(defn fetch-users
  "fetch users via withing-client,
   return the users data in json format.
   (fetch-users true) returns only valid users."
  [& valid?]
  (let [_ (login)
        ret (-> (curl-get (str wc "/api/users"))
                :body
                (json/parse-string true)
                vec)]
    (if valid?
      (filter :valid ret)
      ret)))

(reset! users (fetch-users true))

(defn fetch-measures
  "fetch measures via withing-client,
   return the measures data in json format."
  []
  (let [_ (login)]
    (-> (curl-get (str wc "/api/measures"))
        :body
        (json/parse-string true)
        vec)))

(reset! measures (fetch-measures))

(defn kind
  "{:id 1, :value 1, :description \"Weight (kg)\", :j_desc \"体重 (kg)\"}
   if nil j-desc, returns description."
  [type]
  (let [item (first (filter #(= type (:value %)) @measures))]
    (or (:j_desc item) (:description item))))

(comment
  @users
  (first @users)
  (second @users)
  (kind 1)
  (kind 77)
  :rcf)

(defn fetch-meas
  "Returns `id` measure `type` util today from `days` before."
  [{:keys [id type days]}]
  (log/debug "fetch-meas" id type days)
  (mysql/execute!
   db
   ["select measure, created from meas
     where user_id=? and
           type=? and
           created > current_timestamp - interval ? day
     order by created"
    id type days]))

(comment
  (fetch-meas {:id 16 :type 1 :days 75})
  :rcf)

(defn average
  [xs]
  (/ (reduce + xs) (count xs)))

(defn f-to-f [f]
  (-> f
      (* 100)
      int
      (/ 100.0)))

(comment
  (f-to-f 3.14159265)
  :rcf)

(defn fetch-data
  "Fetch user `id` data.
   fetch user id 51's type 1 and 77 in days 25 ad 75 days,
   (fetch-data 51 [1 77] [25 75])
   if data lacks, returns [[d \"--\"] ...]
   json?"
  [{:keys [id]} types days]
  (log/debug "fetch-data" id types days)
  (cons id
        (for [type types]
          (cons type
                (for [day days]
                  (let [xs (fetch-meas {:id id :type type :days day})]
                    (if (empty? xs)
                      [day "--"]
                      [day (-> (map :meas/measure xs)
                               average
                               f-to-f)])))))))

(comment
  (fetch-data {:id 16} [1] [3 10 75])
  :rcf)

(defn format-one
  [[type & rows]]
  (log/debug rows)
  (str (kind type)
       "\n"
       (apply str (interpose " " (mapv second rows)))
       "\n"))

(defn format-report
  "Returns string"
  [[_ & reports]]
  (str (now)
       "\n"
       (str/join (mapv format-one reports))))

(defn send-report
  "send-report takes two arguments."
  [{:keys [name bot_name]} text]
  (let [url (str lp "/api/push")]
    (log/debug url name bot_name)
    (curl/post url
               {:form-params {:name name
                              :bot bot_name
                              :text text}
                :follow-redirects false})))

(def hkimura (-> (filter #(= "hkimura" (:name %)) @users)
                 first))

;; must use with caution
;; (def saga-user (-> (filter #(= 51 (:id %)) @users)
;;                    first))

(defn help
  [days]
  (str "項目の下の3つの数字はそれぞれ"
       (first days) "日前平均、"
       (second days) "日間平均、"
       (nth days 2) "日間平均です。"
       "-- は欠測。\n"
       "先頭に🟡🔴がある場合は、25日平均、75日平均からの逸脱を表します。"))

(comment
  (fetch-data hkimura [1 76 77] [1 7 28])
  (format-report (fetch-data hkimura [1 76 77] [1 25 75]))
  (send-report hkimura
               (str
                (format-report (fetch-data hkimura [1 76 77] [1 25 75]))
                "\n"
                (help [1 25 75])))
  :rcf)

(defn reports
  "(format-report) の戻り値にヘルプメッセージを出して送信。"
  [users types days]
  (let [ message (help days)]
    (doseq [user users]
      (send-report user
                   (str
                    (format-report (fetch-data user types days))
                    "\n"
                    message)))))

(def admins (atom nil))
(reset! admins [(filter #(= 16 (:id %)) @users)
                (filter #(= 26 (:id %)) @users)])
@admins
(def me (atom (filter #(= 16 (:id %)) @users)))

(comment
  (reports @me [1 76 77] [1 7 28])
  (reports @admins [1 76 77] [1 25 75])
  ; clojure.lang.ExceptionInfo: babashka.curl: status 500 reports /Users/hkim/clojure/withings-reports/bb/reports.clj:11:5
  :rcf)

(defn -main
  [& args]
  (reports @users [1 76 77] [1 7 28]))
