(ns lens.app
  (:require [lens.route :refer [routes]]
            [lens.handler :refer [handlers]]
            [lens.middleware.datomic :refer [wrap-connection]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [lens.middleware.wan-exception :refer [wrap-exception]]
            [lens.middleware.cors :refer [wrap-cors]]
            [bidi.ring :as bidi-ring]
            [io.clojure.liberator-transit]
            [ring.middleware.format :refer [wrap-restful-format]]
            [bidi.bidi :as bidi]))

(defn path-for [routes]
  (fn [handler & params]
    (apply bidi/path-for routes handler params)))

(defn wrap-not-found [handler]
  (fn [req]
    (if-let [resp (handler req)]
      resp
      {:status 404
       :body {:error "Not Found."}})))

(defn app [db-uri token-introspection-uri context-path]
  {:pre [(re-matches #"/(?:.*/)?" context-path)]}
  (let [routes (routes context-path)]
    (-> (bidi-ring/make-handler routes (handlers (path-for routes)
                                                 token-introspection-uri))
        (wrap-not-found)
        (wrap-exception)
        (wrap-restful-format)
        (wrap-cors)
        (wrap-connection db-uri)
        (wrap-keyword-params)
        (wrap-params))))
