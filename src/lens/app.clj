(ns lens.app
  (:require [lens.routes :refer [routes]]
            [lens.middleware.datomic :refer [wrap-connection]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [lens.middleware.wan-exception :refer [wrap-exception]]
            [lens.middleware.cors :refer [wrap-cors]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [bidi.ring :as bidi-ring]
            [io.clojure.liberator-transit]))

(defn app [db-uri _]
  (-> (bidi-ring/make-handler routes)
      (wrap-exception)
      (wrap-cors)
      (wrap-connection db-uri)
      (wrap-keyword-params)
      (wrap-params)))
