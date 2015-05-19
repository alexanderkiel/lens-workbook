(ns system
  (:use plumbing.core)
  (:require [clojure.string :as str]
            [org.httpkit.server :refer [run-server]]
            [datomic.api :as d]
            [lens.app :refer [app]]
            [lens.util :refer [parse-int]]
            [lens.schema :as schema])
  (:import [java.io File]))

(defn env []
  (if (.canRead (File. ".env"))
    (->> (str/split-lines (slurp ".env"))
         (reduce (fn [ret line]
                   (let [vs (str/split line #"=")]
                     (assoc ret (first vs) (str/join "=" (rest vs))))) {}))
    {}))

(defn create-mem-db []
  (let [uri "datomic:mem://lens"]
    (d/create-database uri)
    (schema/load-schema (d/connect uri))
    uri))

(defn system [env]
  {:app app
   :db-uri (or (env "DB_URI") (create-mem-db))
   :token-introspection-uri (env "TOKEN_INTROSPECTION_URI")
   :context-path (or (env "CONTEXT_PATH") "/")
   :version (System/getProperty "lens-workbook.version")
   :port (or (some-> (env "PORT") (parse-int)) 5000)})

(defnk start [app port & more :as system]
  (let [stop-fn (run-server (app more) {:port port})]
    (assoc system :stop-fn stop-fn)))

(defn stop [{:keys [stop-fn] :as system}]
  (stop-fn)
  (dissoc system :stop-fn))
