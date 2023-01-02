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

(def wc (or (System/getenv "WC") "https://wc.kohhoh.jp"))

(def admin    (System/getenv "WC_LOGIN"))
(def password (System/getenv "WC_PASSWORD"))

(def cookie "cookie.txt")

(def users (atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; utils
(defn curl-get [url & params]
  (curl/get url {:raw-args (vec (concat ["-b" cookie] params))}))

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
(defn fetch-meas
  "returns [{:meas/measure 81.2 :meas/created #inst \"2022-12-11..\"} ...]"
  [id type since]
  (log/info "fetch-meas" id type since)
  (mysql/execute!
   db
   ["select measure from meas
     where user_id=? and type=? and created >?"
    id type since]))

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
  (fetch-meas 51 1 "2022-12-10")
  (fetch-meas 16 1 "2022-11-20")
  (fetch-meas-before 16 1 75)
  :rcf)

;; babashka does not catch
;; java.lang.ArithmeticException: Divide by zero reports
(defn average
  [xs]
  (/ (reduce + xs) (count xs)))

(comment
  (try
    (average [])
    (catch Exception e (.getMessage e)))
  :rcf)

(defn find-user [n]
  (first (filter #(= n (:id %)) @users)))

;; changed type -> types
(defn make-report
  "Fetch user `id` data, line-push with comments.
   returns [[day1 ave1] [day2 ave2] [day3 ave3]]
   if data lacks, returns [[d \"none\"] ...]"
  [id types days]
  (for [type types]
    (cons type
          (for [d days]
            (let [xs (fetch-meas-before id type d)]
              (if (empty? xs)
                [d "none"]
                [d (average (map :meas/measure xs))]))))))

(comment
  (try
    (make-report 51 [1 77] [25 75])
    (catch Exception e (.getMessage e)))
  (try
    (make-report 16 [1] [10 20 30])
    (catch Exception e (.getMessage e)))
  :rcf)

(defn send-report
  [line_id bot_name message]
  (log/info "send-report" line_id bot_name message)
  )

(defn reports
  [users types days]
  (for [user users]
    (make-report (:id user) types days)))

(comment
  (reports @users [1 77] [25 75])
  :rcf)
