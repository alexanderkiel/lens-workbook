(ns lens.test-util
  (:require [datomic.api :as d]
            [lens.schema :refer [load-schema]]
            [clojure.edn :as edn]
            [lens.api :as api]))

(defn connect [] (d/connect "datomic:mem:test"))

(defn database-fixture [f]
  (do
    (d/create-database "datomic:mem:test")
    (load-schema (connect)))
  (f)
  (d/delete-database "datomic:mem:test"))

(defn path-for [handler & args] (pr-str {:handler handler :args args}))

(defn request [method & kvs]
  (reduce-kv
    (fn [m k v]
      (if (sequential? k)
        (assoc-in m k v)
        (assoc m k v)))
    {:request-method method
     :headers {"accept" "*/*"}
     :path-for path-for
     :db-uri "datomic:mem:test"
     :params {}}
    (apply hash-map kvs)))

(defn execute [handler method & kvs]
  (handler (apply request method kvs)))

(defn location [resp]
  (edn/read-string (get-in resp [:headers "Location"])))

(defn href [x]
  (edn/read-string (:href x)))

(defn up-href [resp]
  (href (-> resp :body :links :up)))

(defn self-href [resp]
  (href (-> resp :body :links :self)))

(defn error-msg [resp]
  (-> resp :body :data :message))

(defn create-workbook
  ([]
   (create-workbook "name-100326"))
  ([name]
   (api/create-private-workbook! (connect) "user-id-164629" name)))
