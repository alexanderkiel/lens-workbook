(ns lens.app
  (:require [lens.routes :refer [routes]]
            [lens.middleware.datomic :refer [wrap-connection]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [lens.middleware.wan-exception :refer [wrap-exception]]
            [lens.middleware.cors :refer [wrap-cors]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [bidi.ring :as bidi-ring]))

(defn app [db-uri _]
  (-> routes
      (bidi-ring/make-handler)
      (wrap-exception)
      (wrap-restful-format)
      (wrap-cors)
      (wrap-connection db-uri)
      (wrap-keyword-params)
      (wrap-params)))
