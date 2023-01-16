(ns withings-reports.reports
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

(def debug println) ; or log/debug

(def lack "😰")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; utils
(def cookie "reports.txt")

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

(defn f-to-f [f]
  (-> f
      (* 100)
      int
      (/ 100.0)))

(defn average
  [xs]
  (/ (reduce + xs) (count xs)))

(defn sq [x] (* x x))

;; FIXME: denominator = n-1 だとエラーのケースが増える。
;;        エラーになった時どうする？
(defn sd
  "return Standard Deviation. denominator = n-1."
  [xs]
  (let [x-bar (average xs)
        n (- (count xs) 1)]
    (try
      (sqrt (/ (reduce + (map #(sq (- x-bar %)) xs)) n))
      (catch Exception e
        (do
          (log/info "sd" (.getMessage e))
          0)))))

(comment
  (f-to-f 3.14159265)
  (f-to-f (average (range 10)))
  (sd (range 10))
  :rcf)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; login, users, measures

(def users (atom nil))
(def measures (atom nil))

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
  (log/info "fetch-meas" id type days)
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

(defn fetch-average
  "Fetch user id's averaged data.
   when want user id 51's type 1 and 77 in 1, 7 and 28 days averaged value,
   (fetch-average 51 [1 77] [1 7 28])
   if data lacks, returns [[d (lack-symbol)] ...]"
  [{:keys [id]} types days]
  (log/info "fetch-average" id types days)
  (vec
   (for [type types]
     {:type type
      :values (vec
              (for [day days]
                (let [xs (fetch-meas {:id id :type type :days day})]
                  {:days day
                   :average (if (empty? xs)
                              lack
                              (-> (map :meas/measure xs)
                                  average
                                  f-to-f))})))})))

(defn fetch-sd
  "Fetch user-id's sd values.
   when want user id 51's type 1 and 77 in 25 and 75 days SD value,
   (fetch-average 51 [1 77] [25 75])
   if data lacks, returns [[d (lack-symbol)] ...]"
  [{:keys [id]} types days]
  (log/info "fetch-sd" id types days)
  (vec
   (for [type types]
    {:type type
     :values
     (vec
      (for [day days]
       (let [xs (fetch-meas {:id id :type type :days day})]
         {:days day
          :sd (if (empty? xs)
                   lack
                   (-> (map :meas/measure xs)
                       sd
                       f-to-f))})))})))

(comment
  [(fetch-average {:id 16} [1 77 78] [1 7 28])
   (fetch-average {:id 16} [1 77 78] [25 75])
   (fetch-sd {:id 16} [1 77 78] [25 75])]
  :rcf)

(defn help
  [days]
  (str "項目の下の3つの数字はそれぞれ"
       (first days) "日前平均、"
       (second days) "日間平均、"
       (nth days 2) "日間平均です。"
       lack "は欠測。\n"
       "先頭に🟡🔴がある場合は、"
       "1日前平均が25日平均、75日平均からの逸脱を表します。"))

;; make-report
(defn get-types [av1]
  (mapv :type av1))

(defn get-days [av]
  (->> av
       first
       :values
       (mapv :days)))

(defn get-averages
  [type av]
  (let [values (->> av
                    (filter #(= type (:type %)))
                    first
                    :values)]
    {:type type :values values}))

(defn get-sd
  "get-average と同じ。"
  [type sd2]
  (get-averages type sd2))

(defn format-one
  [{:keys [type values]}]
  (let [averages (mapv :average values)]
    (log/info "format-one" (kind type))
    (str (kind type)
         "\n"
         (apply str (interpose ", " averages))
         "\n")))

;; (defn format-report
;;   "Returns string"
;;   [[_ & reports]]
;;   (str (now)
;;        "\n"
;;        (str/join  (mapv format-one reports))))

;; 🔵 🟡 🔴
;; warn :day 25
;;      :av1 {:type 1, :values [{:days 2, :average 81.8} {:days 7, :average 81.03} {:days 28, :average 81.03}]}
;;      :av2 {:type 1, :values [{:days 25, :average 81.03} {:days 75, :average 81.26}]}
;;       :sd2 {:type 1, :values [{:days 25, :sd 0.84} {:days 75, :sd 1.12}]}
(defn warn
  [day av1 av2 sd2]
  (let [days (->> av1 :values (mapv :days))
        value (->> av1
                   :values
                   (filter #(= (first days) (:days %)))
                   first
                   :average)
        mean (->> av2
                  :values
                  (filter #(= day (:days %)))
                  first
                  :average)
        sd (->> sd2
                :values
                (filter #(= day (:days %)))
                first
                :sd)]
    ;; (debug "\t" :days days)
    ;; (debug "\t" :value value)
    ;; (debug "\t" :mean mean)
    ;; (debug "\t" :sd sd)
    (log/info "warn" :value value :mean mean :sd sd)
    (if (or (= value lack) (= mean lack) (= sd lack))
      lack
      (let [diff (abs (- value mean))]
        (cond
          (< diff sd) "🟢"
          (< diff (* 2 sd)) "🟡"
          :else "🔴")))))

(defn warns
  [days2 av1 av2 sd2]
  (mapv #(warn % av1 av2 sd2) days2))

(defn make-report
  [av1 av2 sd2]
  (log/info "make-report")
  (let [types (get-types av1)
        days2 (get-days av2)]
    ;;(doall (for [type types]
    (mapv #(let [warns (warns days2
                              (get-averages % av1)
                              (get-averages % av2)
                              (get-sd       % sd2))
                 report (format-one (get-averages % av1))]
             (debug :warns warns)
             (debug :report report)
             (str (str/join warns) report)) types)))

(comment
  (def ex-user hkimura)
  (def av1 (fetch-average ex-user [1 76 77] [3 7 28]))
  (def av2 (fetch-average ex-user [1 76 77] [25 75]))
  (def sd2 (fetch-sd ex-user [1 76 77] [25 75]))
  (format-one (get-averages 1 av1))
  (warns
   (get-days av2)
   (get-averages 1 av1)
   (get-averages 1 av2)
   (get-sd 1 sd2))
  (str/join (make-report av1 av2 sd2))
  :rcf)

(defn send-report
  "send-report takes two arguments."
  [{:keys [name bot_name]} text]
  (let [url (str lp "/api/push")]
    (log/info "send-report" url name bot_name)
    (curl/post url
               {:form-params {:name name
                              :bot bot_name
                              :text text}
                :follow-redirects false})))

;; pmap で並列化とともに、エラー回避できないか？
(defn reports
  "ヘルプメッセージをくっつけて LINE push.
   nosend をつけて呼ぶと送信しない。"
  [users types days days2 & nosend]
  (doseq [user users]
    (let [av1 (fetch-average user types days)
          av2 (fetch-average user types days2)
          sd2 (fetch-sd      user types days2)
          report (str/join (make-report av1 av2 sd2))]
      (if nosend
        (debug :report report)
        (send-report user (str report "\n" (help days)))))))

(comment
  (reports [hkimura] [1 76 77] [1 7 28] [25 75] :debug)
  (reports [hkimura] [1 76 77] [10 7 28] [25 75])
  :rcf)

(defn -main
  [& _args]
  (reports [hkimura] [1 76 77] [1 7 28] [25 75] :debug)
  #_(reports @users [1 76 77] [1 7 28] [25 75]))
