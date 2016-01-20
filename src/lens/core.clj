(ns lens.core
  (:use plumbing.core)
  (:require [com.stuartsierra.component :as comp]
            [environ.core :refer [env]]
            [lens.system :as system]
            [lens.logging :refer [info]]))

(defn- max-memory []
  (quot (.maxMemory (Runtime/getRuntime)) (* 1024 1024)))

(defn- available-processors []
  (.availableProcessors (Runtime/getRuntime)))

(defn -main [& _]
  (letk [[port thread version db-uri context-path token-introspection-uri
          :as system]
         (system/new-system env)]
    (comp/start system)
    (info {:version version})
    (info {:max-memory (max-memory)})
    (info {:num-cpus (available-processors)})
    (info {:datomic db-uri})
    (info {:context-path context-path})
    (info {:token-introspection-uri token-introspection-uri})
    (info {:listen (str "0.0.0.0:" port)})
    (info {:num-worker-threads thread})))
