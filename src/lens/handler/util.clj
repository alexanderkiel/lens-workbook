(ns lens.handler.util
  (:use plumbing.core)
  (:require [lens.route :refer [path-for]]))

(defn decode-etag [etag]
  (subs etag 1 (dec (count etag))))

(defn error-body [msg]
  {:links {:up {:href (path-for :service-document-handler)}}
   :error msg})

(defn error [status msg]
  {:status status
   :body (error-body msg)})

(def resource-defaults
  {:available-media-types ["application/json" "application/transit+json"
                           "application/edn"]

   :service-available?
   (fnk [request]
     (let [conn (:conn request)
           db (:db request)]
       (when conn
         {:conn conn :db db})))

   ;; Just respond with plain text here because the media type is negotiated
   ;; later in the decision graph.
   :handle-unauthorized (fn [{:keys [error]}] (or error "Not authorized."))})