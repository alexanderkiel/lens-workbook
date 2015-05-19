(ns lens.handler
  (:use plumbing.core)
  (:require [clojure.string :as str]
            [clojure.core.async :refer [<!! timeout]]
            [liberator.core :refer [resource]]
            [liberator.representation :refer [as-response]]
            [lens.handler.util :refer :all]
            [lens.handler.version :as version]
            [lens.api :as api]
            [lens.oauth2 :as oauth2])
  (:import [java.net URI]))

(defn workbook-path [path-for workbook]
  (path-for :get-workbook-handler :id (:workbook/id workbook)))

;; ---- Service Document ------------------------------------------------------

(defn find-workbook-form [path-for]
  {:action (path-for :find-workbook-handler)
   :method "GET"
   :title "Find Workbook"
   :params
   {:id
    {:type :string
     :description "The :id of the workbook."}}})

(defn service-document-handler [path-for version]
  (resource
    resource-defaults

    :handle-ok
    {:name "Lens Workbook"
     :version version
     :links
     {:self {:href (path-for :service-document-handler)}
      :lens/private-workbooks {:href (path-for :all-private-workbooks)}}
     :forms
     {:lens/find-workbook (find-workbook-form path-for)}}))

;; ---- Workbook --------------------------------------------------------------

(defn update-workbook-form [path-for workbook]
  {:action (workbook-path path-for workbook)
   :method "PUT"
   :title "Update Workbook"
   :description "Updates the workbook to point to another version."
   :params
   {:version-id
    {:type :string
     :description "The :id of the version to put the workbook on."}}})

(defn render-workbook [path-for workbook]
  {:links
   {:up {:href (path-for :service-document-handler)}
    :self {:href (workbook-path path-for workbook)}
    :lens/head {:href (version/path path-for (:workbook/head workbook))}}
   :forms
   {:lens/update-workbook (update-workbook-form path-for workbook)}
   :id (:workbook/id workbook)
   :name (:workbook/name workbook)})

(defn get-workbook-handler [path-for]
  (resource
    resource-defaults

    :exists?
    (fnk [db [:request [:params id]]]
      (when-let [workbook (api/workbook db id)]
        {:workbook workbook}))

    :etag (fn [{:keys [workbook]}] (-> workbook :workbook/head :version/id))

    :handle-ok
    (fnk [workbook] (render-workbook path-for workbook))

    :handle-not-found
    (error-body path-for "Workbook not found.")))

(defn put-workbook-handler [path-for]
  (fnk [conn [:params id] headers :as req]
    (if-let [new-version-id (:version-id (:params req))]
      (if-let [old-version-id (some-> (headers "if-match") decode-etag)]
        (try
          (let [wb (api/update-workbook! conn id old-version-id new-version-id)]
            {:status 204
             :headers {"etag" (str "\"" (:version/id (:workbook/head wb)) "\"")}})
          (catch Exception e
            (condp = (:type (ex-data e))
              :lens.schema/workbook-not-found (error path-for 404 "Workbook not found.")
              :lens.schema/precondition-failed (error path-for 412 "Precondition failed.")
              :lens.schema/version-not-found (error path-for 422 "Version doesn't exist."))))
        (error path-for 428 "Precondition required."))
      (error path-for 422 "Version id is missing."))))

;; ---- Private Workbooks -----------------------------------------------------

(defn render-embedded-workbook [path-for workbook]
  {:links
   {:self {:href (workbook-path path-for workbook)}}
   :id (:workbook/id workbook)
   :name (:workbook/name workbook)})

(defn all-private-workbooks-handler
  "List of all private workbooks of a user.

  Requires authentication. Also used to create a new private workbook."
  [path-for token-introspection-uri]
  (resource
    resource-defaults

    :allowed-methods [:get :post]

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
        [false {:error "Not authorized."}]))

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
    (fnk [db [:user-info sub]]
      {:links
       {:self {:href (path-for :all-private-workbooks)}}
       :forms
       {:lens/create
        {:action (path-for :all-private-workbooks)
         :method "POST"
         :title "Create A Private Workbook"
         :params
         {:name {:type :string
                 :description "Name of the workbook."}}}}
       :embedded
       {:lens/workbooks
        (if-let [user (api/user db sub)]
          (->> (api/private-workbooks user)
               (mapv #(render-embedded-workbook path-for %)))
          [])}})

    :handle-created
    (fnk [workbook] (render-workbook path-for workbook))))

;; ---- Find Workbook -------------------------------------------------------

(defn find-workbook-handler [path-for]
  (resource
    resource-defaults

    :processable?
    (fnk [[:request params]]
      (:id params))

    :exists?
    (fnk [db [:request [:params id]]]
      (when-let [workbook (api/workbook db id)]
        {:workbook workbook}))

    :etag (fn [{:keys [workbook]}] (-> workbook :workbook/head :version/id))

    :handle-ok
    (fnk [workbook] (render-workbook path-for workbook))

    :handle-not-found
    (error-body path-for "Workbook not found.")

    :handle-unprocessable-entity
    (error-body path-for "Missing query param 'id'.")))

;; ---- Handlers --------------------------------------------------------------

(defnk handlers [path-for version token-introspection-uri]
  {:pre [(URI/create token-introspection-uri)]}
  {:service-document-handler (service-document-handler path-for version)
   :find-workbook-handler (find-workbook-handler path-for)
   :get-workbook-handler (get-workbook-handler path-for)
   :put-workbook-handler (put-workbook-handler path-for)
   :version-handler (version/handler path-for)
   :add-query-handler (version/add-query-handler path-for)
   :remove-query-handler (version/remove-query-handler path-for)
   :duplicate-query-handler (version/duplicate-query-handler path-for)
   :add-query-cell-handler (version/add-query-cell-handler path-for)
   :remove-query-cell-handler (version/remove-query-cell-handler path-for)
   :all-private-workbooks (all-private-workbooks-handler
                            path-for token-introspection-uri)})

