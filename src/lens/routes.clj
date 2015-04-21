(ns lens.routes
  (:use plumbing.core)
  (:require [clojure.core.async :refer [timeout]]
            [clojure.core.reducers :as r]
            [bidi.bidi :as bidi]
            [liberator.core :refer [defresource]]
            [lens.api :as api]))

(declare routes)

(defn path-for [handler & params]
  (apply bidi/path-for routes handler params))

(def media-types ["application/json" "application/edn"])

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

(declare service-document-handler)

(defn service-document-path []
  (path-for service-document-handler))

(declare branch-list-path)

(defresource service-document-handler
  :available-media-types media-types

  :handle-ok
  {:name "Lens Workbook"
   :links
   {:self {:href (service-document-path)}
    :lens/branches {:href (branch-list-path)}}
   :forms
   {:lens/find-workbook (find-workbook-form)
    :lens/create-workbook (create-workbook-form)}})

;; ---- Workbook --------------------------------------------------------------

(declare workbook-handler create-branch-form add-query-form)

(defn workbook-path [workbook]
  (path-for workbook-handler :id (:workbook/id workbook)))

(defn assoc-parent-link [links workbook]
  (if-let [parent (:workbook/parent workbook)]
    (assoc links :lens/parent {:href (workbook-path parent)})
    links))

(defresource workbook-handler
  :available-media-types media-types

  :exists?
  (fnk [[:request [:params db id]]]
    (when-let [workbook (api/workbook db id)]
      {:workbook workbook}))

  :handle-ok
  (fnk [workbook]
    {:links
     (-> {:up {:href (path-for service-document-handler)}
          :self {:href (workbook-path workbook)}
          :lens/queries
          (->> (api/queries workbook)
               (mapv #(hash-map :href (str "/queries/" (:query/id %)))))}
         (assoc-parent-link workbook))
     :forms
     {:lens/create-branch (create-branch-form workbook)
      :lens/add-query (add-query-form workbook)}
     :id (:workbook/id workbook)})

  :handle-not-found
  (fn [_]
    {:links {:up {:href (service-document-path)}}
     :error "Workbook not found."}))

;; ---- Create Workbook -------------------------------------------------------

(declare create-workbook-handler)

(defresource create-workbook-handler
  :allowed-methods [:post]

  :media-type-available? true

  :post!
  (fnk [[:request [:params conn]]]
    {:workbook (api/create-standard-workbook conn)})

  :location
  (fnk [workbook] (workbook-path workbook)))

;; ---- Create Branch ---------------------------------------------------------

(declare create-branch-handler branch-path)

(defn create-branch-path [workbook]
  (path-for create-branch-handler :id (:workbook/id workbook)))

(defn create-branch-form [workbook]
  {:action (create-branch-path workbook)
   :method "POST"
   :title "Create A New Branch"
   :description "The new branch is based on this workbook."})

(defresource create-branch-handler
  :available-media-types media-types
  :allowed-methods [:post]

  :can-post-to-missing? false

  :exists?
  (fnk [[:request [:params db id]]]
    (when-let [workbook (api/workbook db id)]
      {:workbook workbook}))

  :post!
  (fnk [[:request [:params conn]] workbook]
    {:branch (api/create-branch conn workbook)})

  :location
  (fnk [branch] (branch-path branch))

  :handle-not-found
  (fn [_]
    {:links {:up {:href (service-document-path)}}
     :error "Workbook not found."}))

;; ---- Add Query -------------------------------------------------------------

(declare add-query-handler)

(defn add-query-path [workbook]
  (path-for add-query-handler :id (:workbook/id workbook)))

(defresource add-query-handler
  :allowed-methods [:post]

  :media-type-available? true

  :exists?
  (fnk [[:request [:params db id]]]
    (when-let [workbook (api/workbook db id)]
      {:workbook workbook}))

  :post!
  (fnk [[:request [:params conn]] workbook]
    {:workbook (api/add-query conn workbook)})

  :location
  (fnk [workbook] (workbook-path workbook)))

(defn add-query-form [workbook]
  {:action (add-query-path workbook)
   :method "POST"
   :title "Add Query"})

;; ---- Branch ----------------------------------------------------------------

(declare branch-handler)

(defn branch-path [branch]
  (path-for branch-handler :id (:branch/id branch)))

(defresource branch-handler
  :available-media-types media-types

  :exists?
  (fnk [[:request [:params db id]]]
    (when-let [branch (api/branch db id)]
      {:branch branch}))

  :handle-ok
  (fnk [branch]
    {:links
     {:up {:href (service-document-path)}
      :self {:href (branch-path branch)}
      :lens/workbook
      {:href (-> branch :branch/workbook workbook-path)}}
     :forms
     {}})

  :handle-not-found
  (fn [_]
    {:links {:up {:href (service-document-path)}}
     :error "Branch not found."}))

;; ---- Branch List -----------------------------------------------------------

(declare branch-list-handler)

(defn branch-list-path []
  (path-for branch-list-handler))

(defn render-embedded-branch [branch]
  {:links
   {:self {:href (branch-path branch)}
    :lens/workbook {:href (workbook-path (:branch/workbook branch))}}
   :id (:branch/id branch)})

(defn render-embedded-branches [branches]
  (r/map render-embedded-branch branches))

(defresource branch-list-handler
  :allowed-methods [:get :post]
  :available-media-types media-types

  :handle-ok
  (fnk [[:request [:params db]]]
    {:links
     {:up {:href (service-document-path)}
      :self {:href (branch-list-path)}}
     :embedded
     {:lens/branches
      (->> (api/all-branches db)
           (render-embedded-branches)
           (into []))}})

  :handle-not-found
  (fn [_]
    {:links {:up {:href (service-document-path)}}
     :error "Branch not found."}))

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

;; ---- Routes ----------------------------------------------------------------

(def routes
  ["/" {"" service-document-handler
        "workbooks" create-workbook-handler
        ["workbooks/" :id] workbook-handler
        ["workbooks/" :id "/create-branch"] create-branch-handler
        ["workbooks/" :id "/add-query"] add-query-handler
        "branches" branch-list-handler
        ["branches/" :id] branch-handler}])
