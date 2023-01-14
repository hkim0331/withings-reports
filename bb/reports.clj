(ns reports
  (:require
   [babashka.curl :as curl]
   [babashka.pods :as pods]
   [cheshire.core :as json]
   [clojure.java.shell :refer [sh]]
   [clojure.math :refer [sqrt]]
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

(def debug println) ; or log/debug

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

(def hkimura (-> (filter #(= "hkimura" (:name %)) @users)
                 first))

;; must use with caution
;; (def saga-user (-> (filter #(= 51 (:id %)) @users)
;;                    first))

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
  ;; (debug "fetch-meas" id type days)
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

(defn fetch-average
  "Fetch user id's averaged data.
   when want user id 51's type 1 and 77 in 1, 7 and 28 days averaged value,
   (fetch-average 51 [1 77] [1 7 28])
   if data lacks, returns [[d \"--\"] ...]"
  [{:keys [id]} types days]
  (debug "fetch-average" id types days)
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

(defn sq [x] (* x x))

(defn sd
  "return Standard Deviation. denominator = n-1."
  [xs]
  (let [x-bar (average xs)
        n (- (count xs) 1)]
    (sqrt (/ (reduce + (map #(sq (- x-bar %)) xs)) n))))

(comment
  (sd (range 10))
  :rcf)

(defn fetch-sd
  "Fetch user-id's sd values.
   when want user id 51's type 1 and 77 in 25 and 75 days SD value,
   (fetch-average 51 [1 77] [25 75])
   if data lacks, returns [[d \"--\"] ...]"
  [{:keys [id]} types days]
  (debug "fetch-sd" id types days)
  (cons id
        (for [type types]
          (cons type
                (for [day days]
                  (let [xs (fetch-meas {:id id :type type :days day})]
                    (if (empty? xs)
                      [day "--"]
                      [day (-> (map :meas/measure xs)
                               sd
                               f-to-f)])))))))

(comment
  [(fetch-average {:id 16} [1 77 78] [1 7 28])
   (fetch-average {:id 16} [1 77 78] [25 75])
   (fetch-sd {:id 16} [1 77 78] [25 75])]
  :rcf)

(defn format-one
  [[type & rows]]
  (debug "format-one" (kind type) rows)
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
    (debug url name bot_name)
    (curl/post url
               {:form-params {:name name
                              :bot bot_name
                              :text text}
                :follow-redirects false})))

(defn help
  [days]
  (str "項目の下の3つの数字はそれぞれ"
       (first days) "日前平均、"
       (second days) "日間平均、"
       (nth days 2) "日間平均です。"
       "-- は欠測。\n"
       "先頭に🟡🔴がある場合は、25日平均、75日平均からの逸脱を表します。"))

(defn get-types [ave1]
  (->> ave1
      first
      rest
      (map first)))

(defn get-days2 [ave2]
  (get-types ave2))

(defn get-data
  "format-one に渡すデータを作る"
  [type data]
  (filter #(= type (first %)) data))

;; (defn get-type [a]
;;   (first a))

;; (defn get-value [a]
;;   (-> a second second))

;; (defn get-mean [av2 type days]
;;   (->> av2
;;        (filter #(= type (first %)))
;;        (filter #(= days (first %)))
;;        second))

;; (defn get-sd [sd type days]
;;   (get-mean sd type days))

(comment
  (let [av1 '((1 [1 --] [7 93.2] [28 93.55]) (76 [1 --] [7 --] [28 --]) (77 [1 --] [7 --] [28 --]))
        av2 '((1 [25 93.55] [75 93.73]) (76 [25 --] [75 --]) (77 [25 --] [75 --]))
        sd '((1 [25 0.52] [75 0.46]) (76 [25 --] [75 --]) (77 [25 --] [75 --]))]
    (get-types av1)
    (get-days2 av2)
    (get-data 76 av1)
    ;;(get-type (first av1))
    ;;(get-value (first av1))
    )
  :rcf)

(defn warn
  "av1, av2, sd2 は ave1, ave2, sd2 よりも１レベル細かい。"
  [days2 av1 av2 sd2]
  (debug "warn" days2 av1 av2 sd2)
  )
(defn make-report
  [ave1 ave2 sd2]
  (let [types (get-types ave1)
        days2 (get-days2 ave2)]
    (for [type types]
      (let [warns (warn days2
                        (get-data type ave1) ;;
                        (get-data type ave2)
                        (get-data type sd2))
            data (format-one [type (get-data type ave1)])]
        (str warns data)))))

(comment
  (make-report
   (fetch-average hkimura [1 76 77] [1 7 28])
   (fetch-average hkimura [1 76 77] [25 75])
   (fetch-sd hkimura [1 76 77] [25 75]))
  ;;
  (send-report hkimura
               (str
                (make-report
                 (fetch-average hkimura [1 76 77] [1 7 28])
                 (fetch-average hkimura [1 76 77] [25 75])
                 (fetch-sd hkimura [1 76 77] [25 75]))
                "\n"
                (help [1 7 28])))
  :rcf)

(defn reports
  "(format-report) の戻り値にヘルプメッセージを出して送信。"
  [users types days]
  (doseq [user users]
    (send-report user
                 (str
                  (format-report (fetch-average user types days))
                  "\n"
                  (help days)))))

(comment
  (reports [hkimura] [1 76 77] [1 7 28])
  ;;(reports @admins [1 76 77] [1 7 28])
  :rcf)

(defn -main
  [& args]
  (reports @users [1 76 77] [1 7 28]))
