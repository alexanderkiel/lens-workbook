(ns lens.handler
  (:use plumbing.core)
  (:require [clojure.string :as str]
            [clojure.core.async :refer [<!! timeout]]
            [liberator.core :refer [defresource resource]]
            [liberator.representation :refer [as-response]]
            [lens.route :refer [path-for]]
            [lens.handler.util :refer :all]
            [lens.handler.version :as version]
            [lens.api :as api]
            [lens.oauth2 :as oauth2]))

(defn workbook-path [workbook]
  (path-for :get-workbook-handler :id (:workbook/id workbook)))

;; ---- Service Document ------------------------------------------------------

(defn find-workbook-form []
  {:action "/find-workbook"
   :method "GET"
   :title "Find Workbook"
   :params
   {:id
    {:type :string
     :description "The :id of the workbook."}}})

(defn create-workbook-form []
  {:action "/workbooks"
   :method "POST"
   :title "Create A New Workbook"})

(def service-document-handler
  (resource
    resource-defaults

    :handle-ok
    {:name "Lens Workbook"
     :links
     {:self {:href (path-for :service-document-handler)}
      :lens/private-workbooks {:href (path-for :private-workbook-list)}}
     :forms
     {:lens/find-workbook (find-workbook-form)
      :lens/create-workbook (create-workbook-form)}}))

;; ---- Workbook --------------------------------------------------------------

(defn update-workbook-form [workbook]
  {:action (workbook-path workbook)
   :method "PUT"
   :title "Update Workbook"
   :description "Updates the workbook to point to another version."
   :params
   {:version-id
    {:type :string
     :description "The :id of the version to put the workbook on."}}})

(defn render-workbook [workbook]
  {:links
   {:up {:href (path-for :service-document-handler)}
    :self {:href (workbook-path workbook)}
    :lens/head {:href (version/path (:workbook/head workbook))}}
   :forms
   {:lens/update-workbook (update-workbook-form workbook)}
   :id (:workbook/id workbook)
   :name (:workbook/name workbook)})

(def get-workbook-handler
  (resource
    resource-defaults

    :exists?
    (fnk [db [:request [:params id]]]
      (when-let [workbook (api/workbook db id)]
        {:workbook workbook}))

    :etag (fnk [workbook] (-> workbook :workbook/head :version/id))

    :handle-ok
    (fnk [workbook] (render-workbook workbook))

    :handle-not-found
    (error-body "Workbook not found.")))

(defnk put-workbook-handler [conn [:params id] headers :as req]
  (if-let [new-version-id (:version-id (:params req))]
    (if-let [old-version-id (some-> (headers "if-match") decode-etag)]
      (try
        (let [wb (api/update-workbook! conn id old-version-id new-version-id)]
          {:status 204
           :headers {"etag" (str "\"" (:version/id (:workbook/head wb)) "\"")}})
        (catch Exception e
          (condp = (:type (ex-data e))
            :lens.schema/workbook-not-found (error 404 "Workbook not found.")
            :lens.schema/precondition-failed (error 412 "Precondition failed.")
            :lens.schema/version-not-found (error 422 "Version doesn't exist."))))
      (error 428 "Precondition required."))
    (error 422 "Version id is missing.")))

;; ---- Private Workbooks -----------------------------------------------------

(defn render-embedded-workbook [workbook]
  {:links
   {:self {:href (workbook-path workbook)}}
   :id (:workbook/id workbook)
   :name (:workbook/name workbook)})

(defn private-workbook-list
  "List of all private workbooks of a user.

  Requires authentication. Also used to create a new private workbook."
  [token-introspection-uri]
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
       {:self {:href (path-for :private-workbook-list)}}
       :forms
       {:lens/create
        {:action (path-for :private-workbook-list)
         :method "POST"
         :title "Create A Private Workbook"
         :params
         {:name {:type :string
                 :description "Name of the workbook."}}}}
       :embedded
       {:lens/workbooks
        (if-let [user (api/user db sub)]
          (->> (api/private-workbooks user)
               (mapv render-embedded-workbook))
          [])}})

    :handle-created
    (fnk [workbook] (render-workbook workbook))))

;; ---- Find Workbook -------------------------------------------------------

(def find-workbook-handler
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
    (fnk [workbook] (render-workbook workbook))

    :handle-not-found
    (error-body "Workbook not found.")))

;; ---- Handlers --------------------------------------------------------------

(defn handlers [token-introspection-uri]
  {:service-document-handler service-document-handler
   :find-workbook-handler find-workbook-handler
   :get-workbook-handler get-workbook-handler
   :put-workbook-handler put-workbook-handler
   :version-handler version/handler
   :add-query-handler version/add-query-handler
   :remove-query-handler version/remove-query-handler
   :add-query-cell-handler version/add-query-cell-handler
   :remove-query-cell-handler version/remove-query-cell-handler
   :private-workbook-list (private-workbook-list token-introspection-uri)})

