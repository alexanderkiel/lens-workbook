(ns lens.server
  (:require [com.stuartsierra.component :refer [Lifecycle]]
            [org.httpkit.server :refer [run-server]]
            [schema.core :as s :refer [Str]]
            [lens.app :refer [app]]
            [lens.route :refer [ContextPath]]))

(defrecord Server [version context-path db-uri token-introspection-uri port
                   thread stop-fn]
  Lifecycle
  (start [server]
    (let [handler (app {:version version
                        :db-uri db-uri
                        :context-path context-path
                        :token-introspection-uri token-introspection-uri})
          opts {:port port :thread thread}]
      (assoc server :stop-fn (run-server handler opts))))
  (stop [server]
    (stop-fn)
    (assoc server :stop-fn nil)))

(s/defn new-server []
  (map->Server {}))
