(ns lens.app
  (:use plumbing.core)
  (:require [clojure.tools.logging :as log]
            [bidi.bidi :as bidi]
            [bidi.ring :as bidi-ring]
            [io.clojure.liberator-transit]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [lens.route :refer [routes]]
            [lens.handler :refer [handlers]]
            [lens.middleware.datomic :refer [wrap-connection]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [lens.middleware.wan-exception :refer [wrap-exception]]
            [lens.middleware.cors :refer [wrap-cors]]))

(defn path-for [routes]
  (fn [handler & params]
    (apply bidi/path-for routes handler params)))

(defn wrap-not-found [handler]
  (fn [req]
    (if-let [resp (handler req)]
      resp
      {:status 404})))

(defn- duration [start end]
  (Math/round (/ (double (- end start)) 1000000)))

(defn wrap-log [handler]
  (fn [req]
    (let [start (System/nanoTime)
          resp (handler req)
          end (System/nanoTime)]
      (log/info (pr-str {:request (select-keys req [:remote-addr :request-method
                                                    :uri :query-string :headers])
                         :response (select-keys resp [:status :headers])
                         :duration (str (duration start end) " ms")}))
      resp)))

(defnk app [db-uri context-path :as opts]
  (assert (re-matches #"/(?:.*[^/])?" context-path))
  (let [routes (routes context-path)
        opts (assoc opts :path-for (path-for routes))]
    (-> (bidi-ring/make-handler routes (handlers opts))
        (wrap-not-found)
        (wrap-exception)
        (wrap-cors)
        (wrap-connection db-uri)
        (wrap-keyword-params)
        (wrap-params)
        (wrap-log))))
