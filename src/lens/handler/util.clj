(ns lens.handler.util
  (:use plumbing.core)
  (:require [liberator.core :as l :refer [resource]]
            [liberator.representation :refer [Representation as-response]]
            [datomic.api :as d]
            [schema.core :as s]
            [schema.coerce :as c]
            [schema.utils :as su]
            [digest.core :as digest]
            [clojure.tools.logging :as log])
  (:refer-clojure :exclude [error-handler]))

(defn decode-etag [etag]
  (subs etag 1 (dec (count etag))))

(defn error-body [path-for msg]
  {:data {:message msg}
   :links {:up {:href (path-for :service-document-handler)}}})

(defn ring-error
  "Error as ring response.

  Can't be used with liberator handlers."
  [path-for status msg]
  {:status status
   :body (error-body path-for msg)})

(defrecord StatusResponse [status response]
  Representation
  (as-response [_ context]
    (assoc (as-response response context) :status status)))

(defn error [path-for status msg]
  (->StatusResponse status (error-body path-for msg)))

(defn handle-cache-control [resp opts]
  (if-let [cache-control (:cache-control opts)]
    (assoc-in resp [:headers "cache-control"] cache-control)
    resp))

(extend-protocol Representation
  clojure.lang.MapEquivalence
  (as-response [this _] {:body this}))

(defn error-handler [msg]
  (fnk [[:request path-for] :as ctx]
    (error-body path-for (or (:error ctx) msg))))

(defnk db-available? [[:request db-uri request-method]]
  (try
    (let [conn (d/connect db-uri)
          ctx {:db (d/db conn)}]
      (if (#{:post :put :delete} request-method)
        (assoc ctx :conn conn)
        ctx))
    (catch Throwable e
      (let [msg (str "Error connecting to " db-uri ": " (.getMessage e))]
        (log/error msg)
        [false {:error msg}]))))

(defn resource-defaults [& {:as opts}]
  {:available-media-types ["application/json" "application/transit+json"]

   :encoding-available? true
   :charset-available? true

   :as-response
   (fn [data ctx]
     (handle-cache-control (as-response data ctx) opts))

   :handle-service-not-available (error-handler "Service not available.")
   :handle-unauthorized (error-handler "Not authorized.")
   :handle-malformed
   (fnk [error [:request path-for] :as ctx]
     (if (= "Require conditional update." error)
       (lens.handler.util/error path-for 428 error)
       ((error-handler "Malformed") ctx)))
   :handle-unprocessable-entity (error-handler "Unprocessable Entity")
   :handle-precondition-failed (error-handler "Precondition Failed")
   :handle-not-found (error-handler "Not Found")

   :handle-not-modified nil})

(defnk entity-malformed
  "Standard malformed decision for single entity resources.

  Parsed entity will be placed under :new-entity in the context in case of
  success. Otherwise :error will be placed."
  [request :as ctx]
  (if (or (not (l/=method :put ctx)) (l/header-exists? "if-match" ctx))
    (when (l/=method :put ctx)
      (if-let [body (:body request)]
        [false {:new-entity (:data body)}]
        {:error "Missing request body."}))
    {:error "Require conditional update."}))

(defn validate [schema x]
  (if-let [error (s/check schema x)]
    [false {:error (str "Unprocessable Entity: " (pr-str error))}]
    true))

(defn validate-params [schema]
  (fnk [[:request params]]
    (validate schema params)))

(defn coerce [schema params]
  (let [coercer (c/coercer schema c/string-coercion-matcher)
        params (coercer params)]
    (if (su/error? params)
      [false {:error (str "Unprocessable Entity: " (su/error-val params))}]
      {:request {:params params}})))

(defn coerce-params [schema]
  (fnk [[:request params]]
    (coerce schema params)))

(defn entity-processable [schema]
  (fn [ctx]
    (or (not (l/=method :put ctx))
        (validate schema (:new-entity ctx)))))

(defn standard-entity-resource-defaults [& opts]
  (assoc
    (apply resource-defaults opts)

    :allowed-methods [:get :put :delete]

    :malformed? entity-malformed

    :can-put-to-missing? false

    :new? false

    :handle-no-content
    (fnk [[:request path-for] :as ctx]
      (condp = (:update-error ctx)
        :not-found (error path-for 404 "Not Found")
        :conflict (error path-for 409 "Conflict")
        nil))))

(defn etag
  "Returns a function which generates an ETag from context.

  Elements of more can be values or functions. Each function is called with the
  context."
  [& more]
  (fn [ctx]
    (when-let [media-type (-> ctx :representation :media-type)]
      (apply digest/md5 media-type (map #(if (fn? %) (% ctx) %) more)))))

(defn profile-handler [key schema]
  (let [name (keyword (str (name key) "-profile-handler"))]
    (resource
      (resource-defaults :cache-control "max-age=3600")

      :etag (etag 1)

      :handle-ok
      (fnk [[:request path-for]]
        {:data
         {:schema schema}
         :links
         {:up {:href (path-for :service-document-handler)}
          :self {:href (path-for name)}}}))))

;; TODO: remove on release of https://github.com/clojure-liberator/liberator/pull/201
(defmethod l/to-location java.net.URI [uri] (l/to-location (str uri)))

(defn redirect-resource-defaults []
  (assoc
    (resource-defaults :cache-control "no-cache")

    :exists? false
    :existed? true
    :moved-permanently? true))
