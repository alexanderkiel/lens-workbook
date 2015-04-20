(ns system
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
    (schema/load-base-schema (d/connect uri))
    uri))

(defn system [env]
  {:app app
   :db-uri (or (env "DB_URI") (create-mem-db))
   :version (System/getProperty "lens.version")
   :port (or (some-> (env "PORT") (parse-int)) 5002)
   :mdb-uri (env "MDB_URI")})

(defn start [{:keys [app db-uri version port] :as system}]
  (let [stop-fn (run-server (app db-uri version) {:port port})]
    (assoc system :stop-fn stop-fn)))

(defn stop [{:keys [stop-fn] :as system}]
  (stop-fn)
  (dissoc system :stop-fn))
