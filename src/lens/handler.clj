(ns lens.handler
  (:use plumbing.core)
  (:require [clojure.string :as str]
            [clojure.core.async :refer [<!! timeout]]
            [clojure.core.reducers :as r]
            [liberator.core :refer [defresource resource]]
            [liberator.representation :refer [as-response]]
            [lens.route :refer [path-for]]
            [lens.api :as api]
            [lens.oauth2 :as oauth2]))

(defn decode-etag [etag]
  (subs etag 1 (dec (count etag))))

(defn error-body [msg]
  {:links {:up {:href (path-for :service-document-handler)}}
   :error msg})

(defn error [status msg]
  {:status status
   :body (error-body msg)})

(defn workbook-path [workbook]
  (path-for :workbook-handler :id (:workbook/id workbook)))

(defn branch-path [branch]
  (path-for :get-branch-handler :id (:branch/id branch)))

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

(defresource service-document-handler
  resource-defaults

  :handle-ok
  {:name "Lens Workbook"
   :links
   {:self {:href (path-for :service-document-handler)}
    :lens/private-workbooks {:href (path-for :private-workbook-list)}}
   :forms
   {:lens/find-workbook (find-workbook-form)
    :lens/create-workbook (create-workbook-form)}})

;; ---- Workbook --------------------------------------------------------------

(defn assoc-parent-link [links workbook]
  (if-let [parent (:workbook/parent workbook)]
    (assoc links :lens/parent {:href (workbook-path parent)})
    links))

(defn create-branch-form [workbook]
  {:action (path-for :create-branch-handler :id (:workbook/id workbook))
   :method "POST"
   :title "Create A New Branch"
   :description "The new branch is based on this workbook."
   :params
   {:name {:type :string
           :description "Name of the branch."}}})

(defn add-query-form [workbook]
  {:action (path-for :add-query-handler :id (:workbook/id workbook))
   :method "POST"
   :title "Add Query"})

(defn render-workbook [workbook]
  {:links
   (-> {:up {:href (path-for :service-document-handler)}
        :self {:href (workbook-path workbook)}
        :lens/queries
        (->> (api/queries workbook)
             (mapv #(hash-map :href (str "/queries/" (:query/id %)))))}
       (assoc-parent-link workbook))
   :forms
   {:lens/create-branch (create-branch-form workbook)
    :lens/add-query (add-query-form workbook)}
   :id (:workbook/id workbook)
   :name (:workbook/name workbook)})

(defresource workbook-handler
  resource-defaults

  :exists?
  (fnk [db [:request [:params id]]]
    (when-let [workbook (api/workbook db id)]
      {:workbook workbook}))

  :handle-ok
  (fnk [workbook] (render-workbook workbook))

  :handle-not-found
  (error-body "Workbook not found."))

;; ---- Private Workbooks -----------------------------------------------------

(defn render-embedded-workbook [workbook]
  {:links
   {:self {:href (workbook-path workbook)}}
   :id (:workbook/id workbook)
   :name (:workbook/name workbook)})

(defn private-workbook-list [token-introspection-uri]
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
    (fnk [[:request request-method params] :as ctx]
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

(defresource find-workbook-handler
  resource-defaults

  :processable?
  (fnk [[:request params]]
    (:id params))

  :handle-ok
  (fnk [db [:request [:params id]]]
    (render-workbook (api/workbook db id))))

;; ---- Create Branch ---------------------------------------------------------

(defresource create-branch-handler
  resource-defaults

  :allowed-methods [:post]

  :can-post-to-missing? false

  :exists?
  (fnk [db [:request [:params id]]]
    (when-let [workbook (api/workbook db id)]
      {:workbook workbook}))

  :processable?
  (fnk [[:request params]]
    (:name params))

  :post!
  (fnk [conn [:request [:params name]] workbook]
    {:branch (api/create-branch conn workbook name)})

  :location
  (fnk [branch] (branch-path branch))

  :handle-not-found
  (error-body "Workbook not found.")

  :handle-unprocessable-entity
  (error-body "Branch name is missing."))

;; ---- Add Query -------------------------------------------------------------

(defresource add-query-handler
  resource-defaults

  :allowed-methods [:post]

  :media-type-available? true

  :exists?
  (fnk [db [:request [:params id]]]
    (when-let [workbook (api/workbook db id)]
      {:workbook workbook}))

  :post!
  (fnk [conn workbook]
    {:workbook (api/add-query conn workbook)})

  :location
  (fnk [workbook] (workbook-path workbook)))

;; ---- Branch ----------------------------------------------------------------

(defn update-branch-form [branch]
  {:action (branch-path branch)
   :method "PUT"
   :title "Update Branch"
   :description "Updates the branch to point to another workbook."
   :params
   {:workbook-id
    {:type :string
     :description "The :id of the workbook to put the branch on."}}})

(defresource get-branch-handler
  resource-defaults

  :allowed-methods [:get]

  :exists?
  (fnk [db [:request [:params id]]]
    (when-let [branch (api/branch db id)]
      {:branch branch}))

  :etag (fnk [branch] (-> branch :branch/workbook :workbook/id))

  :handle-ok
  (fnk [branch]
    {:links
     {:up {:href (path-for :service-document-handler)}
      :self {:href (branch-path branch)}
      :lens/workbook
      {:href (-> branch :branch/workbook workbook-path)}}
     :forms
     {:lens/update-branch (update-branch-form branch)}
     :name (:branch/name branch)})

  :handle-not-found
  (error-body "Branch not found."))

(defnk put-branch-handler [conn [:params id] headers :as req]
  (if-let [new-workbook-id (:workbook-id (:params req))]
    (if-let [old-workbook-id (some-> (headers "if-match") decode-etag)]
      (try
        (api/update-branch! conn id old-workbook-id new-workbook-id)
        {:status 204}
        (catch Exception e
          (condp = (:type (ex-data e))
            :lens.schema/branch-not-found (error 404 "Branch not found.")
            :lens.schema/precondition-failed (error 412 "Precondition failed.")
            :lens.schema/workbook-not-found (error 422 "Workbook doesn't exist."))))
      (error 428 "Precondition required."))
    (error 422 "Workbook id is missing.")))

;; ---- Branch List -----------------------------------------------------------

(defn render-embedded-branch [branch]
  {:links
   {:self {:href (branch-path branch)}
    :lens/workbook {:href (workbook-path (:branch/workbook branch))}}
   :id (:branch/id branch)})

(defn render-embedded-branches [branches]
  (r/map render-embedded-branch branches))

(defresource branch-list-handler
  resource-defaults

  :handle-ok
  (fnk [db]
    {:links
     {:up {:href (path-for :service-document-handler)}
      :self {:href (path-for :branch-list-handler)}}
     :embedded
     {:lens/branches
      (->> (api/all-branches db)
           (render-embedded-branches)
           (into []))}}))

;; ---- Query -----------------------------------------------------------------

(defn render-embedded-query-cell [cell]
  {:links
   {:self {:href (str "/query-cells/" (:query-cell/id cell))}}
   :type (:query-cell.term/type cell)
   :id (:query-cell.term/id cell)})

(defn render-embedded-query-col [col]
  {:links
   {:self {:href (str "/query-cols/" (:query-col/id col))}}
   :embedded
   {:lens/query-cells
    (->> (api/query-cells col)
         (mapv #(render-embedded-query-cell %)))}})

(defn query [db id]
  (if-let [query (api/query db id)]
    {:links
     {:up {:href (str "/workbooks/" (-> query :query/workbook :workbook/id))}
      :self {:href (str "/queries/" (:query/id query))}}
     :embedded
     {:lens/query-cols
      (->> (api/query-cols query)
           (mapv #(render-embedded-query-col %)))}}
    {:links {:up {:href "/"}}
     :error "Query not found."}))

;; ---- Handlers --------------------------------------------------------------

(defn handlers [token-introspection-uri]
  {:service-document-handler service-document-handler
   :find-workbook-handler find-workbook-handler
   :workbook-handler workbook-handler
   :private-workbook-list (private-workbook-list token-introspection-uri)
   :create-branch-handler create-branch-handler
   :add-query-handler add-query-handler
   :branch-list-handler branch-list-handler
   :get-branch-handler get-branch-handler
   :put-branch-handler put-branch-handler})

