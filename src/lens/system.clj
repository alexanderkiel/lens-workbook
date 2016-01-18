(ns lens.system
  (:use plumbing.core)
  (:require [com.stuartsierra.component :as comp]
            [lens.server :refer [new-server]]
            [lens.datomic :refer [new-database-creator]]
            [lens.util :as util]))

(defnk new-system [lens-workbook-version context-path db-uri port
                   token-introspection-uri]
  (comp/system-map
    :version lens-workbook-version
    :context-path context-path
    :db-uri db-uri
    :token-introspection-uri token-introspection-uri
    :port (util/parse-long port)
    :thread 4

    :db-creator
    (comp/using (new-database-creator) [:db-uri])

    :server
    (comp/using
      (new-server)
      [:version :db-uri :port :thread :context-path :token-introspection-uri])))
