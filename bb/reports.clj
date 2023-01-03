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
;; login, users
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; fetch meas
;; (defn fetch-meas
;;   "returns [{:meas/measure 81.2 :meas/created #inst \"2022-12-11..\"} ...]"
;;   [id type since]
;;   (log/info "fetch-meas" id type since)
;;   (mysql/execute!
;;    db
;;    ["select measure from meas
;;      where user_id=? and type=? and created >?"
;;     id type since]))

(defn fetch-meas-before
  "Returns `id` measure `type` util today from `days` before."
  [id type days]
  (mysql/execute!
   db
   ["select measure, created from meas
     where user_id=? and
           type=? and
           created > current_timestamp - interval ? day
     order by created"
    id type days]))

(comment
  ;; (fetch-meas 51 1 "2022-12-10")
  ;; (fetch-meas 16 1 "2022-11-20")
  (fetch-meas-before 16 1 75)
  :rcf)

(defn average
  [xs]
  (/ (reduce + xs) (count xs)))

(defn f-to-f [f]
  (-> f
      (* 100)
      int
      (/ 100.0)))

(f-to-f 3.14159265)

;; changed type -> types
(defn fetch-data
  "Fetch user `id` data.
   (fetch-data 51 [1 77] [25 75])
   if data lacks, returns [[d \"--\"] ...]
   json?"
  [{:keys [id]} types days]
  (cons id
        (for [type types]
         (cons type
                (for [day days]
                  (let [xs (fetch-meas-before id type day)]
                    (if (empty? xs)
                      [day "--"]
                      [day (-> (map :meas/measure xs)
                               average
                               f-to-f)])))))))

;; use interleave?
(defn format-one
  [[type & rows]]
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
  [{:keys [name bot_name]} report]
  (let [url (str lp "/api/push")]
    (println url name bot_name report)
    #_(curl/post url
                 {:form-params {:name name
                                :bot bot_name
                                :text report}
                  :follow-redirects false})))

(def hkimura (-> (filter #(= "hkimura" (:name %)) @users)
                 first))

(def saga-user (-> (filter #(= 51 (:id %)) @users)
                   first))

(comment
  (fetch-data hkimura [1 76 77] [1 25 75])
  (format-report (fetch-data hkimura [1 76 77] [1 25 75]))
  (format-report (fetch-data saga-user [1 76 77] [1 25 75]))
  (send-report hkimura
               (format-report (fetch-data hkimura [1 76 77] [1 25 75])))
  (send-report saga-user
               (format-report (fetch-data saga-user [1 76 77] [1 25 75])))
  :rcf)

(defn reports
  [users types days]
  (for [user users]
    (send-report user (format-report (fetch-data user types days)))))

(comment
   (reports @users [1 77] [25 75])
   :rcf)
