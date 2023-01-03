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

(comment
  (today)
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

(comment
  @users
  (first @users)
  (second @users)
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

;; (defn find-user [n]
;;   (first (filter #(= n (:id %)) @users)))

;; changed type -> types
(defn fetch-data
  "Fetch user `id` data.
   (fetch-data 51 [1 77] [25 75])
   if data lacks, returns [[d \"none\"] ...]
   json?"
  [id types days]
  (cons id
        (for [type types]
         (cons type
                (for [day days]
                  (let [xs (fetch-meas-before id type day)]
                    (if (empty? xs)
                      [day "none"]
                      [day (-> (map :meas/measure xs)
                               average
                               f-to-f)])))))))

(comment
  (try
    (fetch-data 51 [1 77] [25 75])
    (catch Exception e (.getMessage e))
    )
  (try
    (fetch-data 16 [1] [10 20 30])
    (catch Exception e (.getMessage e))
    )
  :rcf)

(defn send-report
  [{:keys [name bot_name]} report]
  (let [url (str lp "/api/push")]
    (log/info url name bot_name report)
    (curl/post url
               {:form-params {:name name
                              :bot bot_name
                              :text report}
                :follow-redirects false})))

;; FIXME
(defn make-report
  [data]
  (str data))

(comment
  (make-report (fetch-data 16 [1 5 77] [1 25 75]))
  (send-report {:name "hkimura" :bot_name "SAGA-JUDO"}
               (make-report (fetch-data 16 [1 5 77] [1 25 75])))
  :rcf)

(defn reports
  [users types days]
  (for [user users]
    (send-report user (make-report (fetch-data (:id user) types days)))))

;; まだ早い。
;; (comment
;;   (reports @users [1 77] [25 75])
;;   :rcf)
