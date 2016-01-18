(ns lens.handler
  (:use plumbing.core)
  (:require [clojure.string :as str]
            [clojure.core.async :refer [<!! timeout]]
            [liberator.core :refer [resource negotiate-media-type]]
            [liberator.representation :refer [as-response]]
            [lens.handler.util :as hu]
            [lens.handler.version :as version]
            [lens.api :as api]
            [lens.oauth2 :as oauth2]
            [schema.core :refer [Str Any]])
  (:import [java.net URI]))

(defn workbook-path [path-for workbook]
  (path-for :workbook-handler :id (:workbook/id workbook)))

;; ---- Service Document ------------------------------------------------------

(defn find-workbook-query [path-for]
  {:href (path-for :find-workbook-handler)
   :label "Find Workbook"
   :params
   {:id
    {:type Str
     :desc "The :id of the workbook."}}})

(defn service-document-handler [version]
  (resource
    (hu/resource-defaults :cache-control "max-age=60")

    :etag (hu/etag 1)

    :handle-ok
    (fnk [[:request path-for]]
      {:data
       {:name "Lens Workbook"
        :version version}
       :links
       {:self {:href (path-for :service-document-handler)}
        :lens/private-workbooks {:href (path-for :all-private-workbooks)}}
       :queries
       {:lens/find-workbook
        (find-workbook-query path-for)}})))

;; ---- Workbook --------------------------------------------------------------

(defnk render-workbook [workbook [:request path-for]]
  {:data
   {:id (:workbook/id workbook)
    :name (:workbook/name workbook)
    :head-id (:version/id (:workbook/head workbook))}
   :links
   {:up {:href (path-for :service-document-handler)}
    :self {:href (workbook-path path-for workbook)}
    :lens/head {:href (version/path path-for (:workbook/head workbook))}
    :profile {:href (path-for :workbook-profile-handler)}}
   :ops
   #{:update}})

(def Workbook
  {:head-id Str Any Any})

(def workbook-handler
  (resource
    (hu/standard-entity-resource-defaults :cache-control "no-cache")

    :service-available? hu/db-available?

    :processable? (hu/entity-processable Workbook)

    :exists?
    (fnk [db [:request [:params id]]]
      (when-let [workbook (api/workbook db id)]
        {:workbook workbook}))

    :etag (hu/etag #(-> % :workbook :workbook/head :version/id) 1)

    :put!
    (fnk [conn workbook [:new-entity head-id]]
      (try
        {:workbook (api/update-workbook! conn workbook head-id)}
        (catch Exception e
          (condp = (:type (ex-data e))
            :lens.schema/workbook-not-found :not-found
            :lens.schema/precondition-failed :precondition-failed
            :lens.schema/version-not-found :unprocessable-entity

            (throw e)))))

    :handle-ok render-workbook

    :handle-no-content
    (fnk [[:request path-for] :as ctx]
      (condp = (:update-error ctx)
        :not-found (hu/error path-for 404 "Workbook not found.")
        :precondition-failed (hu/error path-for 412 "Precondition failed.")
        :unprocessable-entity (hu/error path-for 422 "Version doesn't exist.")
        nil))

    :handle-not-found
    (fnk [[:request path-for]]
      (hu/error-body path-for "Workbook not found."))))

;; ---- Private Workbooks -----------------------------------------------------

(defn render-embedded-workbook [path-for workbook]
  {:data
   {:id (:workbook/id workbook)
    :name (:workbook/name workbook)}
   :links
   {:self {:href (workbook-path path-for workbook)}}})

(defn all-private-workbooks-handler
  "List of all private workbooks of a user.

  Requires authentication. Also used to create a new private workbook."
  [token-introspection-uri]
  (resource
    (hu/resource-defaults)

    :allowed-methods [:get :post]

    :service-available? hu/db-available?

    :authorized?
    (fnk [[:request headers]]
      (if-let [authorization (headers "authorization")]
        (let [[scheme token] (str/split authorization #" ")]
          (if (= "bearer" (.toLowerCase scheme))
            (let [resp (<!! (oauth2/introspect token-introspection-uri token))]
              (if (:user-info resp)
                resp
                [false {:error "Not authorized."}]))
            [false {:error "Unsupported authentication scheme. Expect Bearer."}]))
        [false {:error "Missing Authorization header."}]))

    :processable?
    (fnk [[:request request-method params]]
      (or (= :get request-method) (:name params)))

    :as-response
    (fn [d ctx]
      (let [resp (as-response d ctx)]
        (if (:error ctx)
          (assoc-in resp [:headers "www-authenticate"] "Bearer realm=\"Lens\"")
          resp)))

    :post!
    (fnk [conn [:user-info sub] [:request [:params name]]]
      {:workbook (api/create-private-workbook! conn sub name)})

    :handle-ok
    (fnk [db [:user-info sub] [:request path-for]]
      {:links
       {:up {:href (path-for :service-document-handler)}
        :self {:href (path-for :all-private-workbooks)}}
       :forms
       {:lens/create
        {:href (path-for :all-private-workbooks)
         :label "Create A Private Workbook"
         :params
         {:name
          {:type Str
           :desc "Name of the workbook."}}}}
       :embedded
       {:lens/workbooks
        (if-let [user (api/user db sub)]
          (->> (api/private-workbooks user)
               (mapv #(render-embedded-workbook path-for %)))
          [])}})

    :location
    (fnk [workbook [:request path-for]]
      (workbook-path path-for workbook))))

;; ---- Find Workbook -------------------------------------------------------

(def find-workbook-handler
  (resource
    (hu/redirect-resource-defaults)

    :service-available? hu/db-available?

    :processable?
    (hu/entity-processable {:id Str Any Any})

    :existed?
    (fnk [db [:request [:params id]]]
      (when-let [workbook (api/workbook db id)]
        {:workbook workbook}))

    :location
    (fnk [workbook [:request path-for]] (workbook-path path-for workbook))

    :handle-unprocessable-entity
    (fnk [[:request path-for]]
      (hu/error-body path-for "Missing query param 'id'."))))

;; ---- Handlers --------------------------------------------------------------

(defnk handlers [version token-introspection-uri]
  {:pre [(URI/create token-introspection-uri)]}
  {:service-document-handler (service-document-handler version)
   :find-workbook-handler find-workbook-handler
   :workbook-handler workbook-handler
   :workbook-profile-handler (hu/profile-handler :workbook Workbook)
   :version-handler version/handler
   :add-query-handler version/add-query-handler
   :remove-query-handler version/remove-query-handler
   :duplicate-query-handler version/duplicate-query-handler
   :add-query-cell-handler version/add-query-cell-handler
   :remove-query-cell-handler version/remove-query-cell-handler
   :all-private-workbooks (all-private-workbooks-handler
                            token-introspection-uri)})

