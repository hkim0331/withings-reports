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

(defn average
  [xs]
  (/ (reduce + xs) (count xs)))

(comment
  (average (range 10))
  :rcf)

(defn make-report
  "Fetch user `id` data, line-push with comments."
  [{:keys [id bot_name line_id]} type days]
  (for [d days]
    [d (average
        (map :meas/measure (fetch-meas-before id type d)))]))

(defn find-user [n]
  (first (filter #(= n (:id %)) @users)))

(comment
  (find-user 51)
  (-> (find-user 51)
      (make-report 1 [75]))
  ;; FIXME: java.lang.ArithmeticException: Divide by zero
  (-> (find-user 16)
      (make-report 1 [10]))
  :rcf)

(defn make-reports
  [types days]
  (doseq [user @users]
    (log/debug
     (for [type types]
       (try
         (make-report user type days)
         (catch Exception e
           (log/debug (.getMessage e))))))))


(comment
  (make-reports [1] [1 20])
  :rcf)
