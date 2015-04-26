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
            [ring.middleware.format :refer [wrap-restful-format]]))

(defn wrap-not-found [handler]
  (fn [req]
    (if-let [resp (handler req)]
      resp
      {:status 404
       :body {:error "Not Found."}})))

(defn app [db-uri _]
  (-> (bidi-ring/make-handler routes handlers)
      (wrap-not-found)
      (wrap-exception)
      (wrap-restful-format)
      (wrap-cors)
      (wrap-connection db-uri)
      (wrap-keyword-params)
      (wrap-params)))
