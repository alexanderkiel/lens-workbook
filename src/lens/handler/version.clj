(ns lens.handler.version
  (:use plumbing.core)
  (:require [liberator.core :refer [resource]]
            [lens.route :refer [path-for]]
            [lens.handler.util :refer :all]
            [lens.api :as api]
            [lens.util :as util]))

(defn render-query-cell [cell]
  {:type (:query.cell.term/type cell)
   :id (:query.cell.term/id cell)})

(defn render-query-col [col]
  {:cells
   (->> (api/query-cells col)
        (mapv #(render-query-cell %)))})

(defn render-query [query]
  (-> {:query-grid
       {:cols
        (->> (api/query-cols query)
             (mapv #(render-query-col %)))}}
      (assoc-when :name (:query/name query))))

(defn path [version]
  (path-for :version-handler :id (:version/id version)))

(defn assoc-parent-link [links version]
  (if-let [parent (:version/parent version)]
    (assoc links :lens/parent {:href (path parent)})
    links))

(defn add-query-form [version]
  {:action (path-for :add-query-handler :id (:version/id version))
   :method "POST"
   :title "Add Query"})

(defn remove-query-form [version]
  {:action (path-for :remove-query-handler :id (:version/id version))
   :method "POST"
   :params
   {:idx
    {:type :long
     :description "The index of the query in the list of queries."}}
   :title "Remove Query"})

(defn add-query-cell-form [version]
  {:action (path-for :add-query-cell-handler :id (:version/id version))
   :method "POST"
   :title "Add Query Cell"
   :params
   {:query-idx
    {:type :long
     :description "The index of the query in the list of queries."}
    :col-idx
    {:type :long
     :description "The index of the column in the list of columns of the query identified by query-idx."}
    :term-type
    {:type :string
     :description "The type of the term (like form or item)."}
    :term-id
    {:type :string
     :description "The id of the term (like T00001 or T00001_F0001)."}}})

(defn remove-query-cell-form [version]
  {:action (path-for :remove-query-cell-handler :id (:version/id version))
   :method "POST"
   :title "Remove Query Cell"
   :params
   {:query-idx
    {:type :long
     :description "The index of the query in the list of queries."}
    :col-idx
    {:type :long
     :description "The index of the column in the list of columns of the query identified by query-idx."}
    :term-id
    {:type :string
     :description "The id of the term (like T00001 or T00001_F0001)."}}})

(defn render-version-head [version]
  {:links
   (-> {:up {:href (path-for :service-document-handler)}
        :self {:href (path version)}}
       (assoc-parent-link version))
   :forms
   {:lens/add-query (add-query-form version)
    :lens/remove-query (remove-query-form version)
    :lens/add-query-cell (add-query-cell-form version)
    :lens/remove-query-cell (remove-query-cell-form version)}
   :id (:version/id version)})

(defn render-version [version]
  (assoc (render-version-head version)
    :queries
    (->> (api/queries version)
         (mapv #(render-query %)))))

(def handler
  (resource
    resource-defaults

    :exists?
    (fnk [db [:request [:params id]]]
      (when-let [version (api/version db id)]
        {:version version}))

    :etag (fn [{:keys [version]}] (-> version :version/id))

    :handle-ok
    (fnk [version] (render-version version))

    :handle-not-found
    (error-body "Version not found.")

    :location
    (fnk [version] (path version))))

(def post-resource-defaults
  (merge
    resource-defaults

    {:allowed-methods [:post]
     :can-post-to-missing? false
     :respond-with-entity? true

     :exists?
     (fnk [db [:request [:params id]]]
       (when-let [version (api/version db id)]
         {:version version}))

     :location
     (fnk [version] (path version))

     :handle-created
     (fnk [version] (render-version-head version))}))

(def add-query-handler
  (resource
    post-resource-defaults

    :post!
    (fnk [conn version]
      {:version (api/add-query! conn version)})))

(def remove-query-handler
  (resource
    post-resource-defaults

    :processable?
    (fnk [[:request params]]
      (and (:idx params) (re-matches #"\d+" (:idx params))))

    :post!
    (fnk [conn version [:request [:params idx]]]
      {:version (api/remove-query! conn version (util/parse-int idx))})))

(def add-query-cell-handler
  (resource
    post-resource-defaults

    :processable?
    (fnk [[:request params]]
      (and (:query-idx params) (:col-idx params)
           (:term-type params) (:term-id params)
           (re-matches #"\d+" (:query-idx params))
           (re-matches #"\d+" (:col-idx params))))

    :post!
    (fnk [conn version [:request [:params query-idx col-idx term-type term-id]]]
      {:version (api/add-query-cell! conn version (util/parse-int query-idx)
                                     (util/parse-int col-idx)
                                     [(keyword term-type) term-id])})))

(def remove-query-cell-handler
  (resource
    post-resource-defaults

    :processable?
    (fnk [[:request params]]
      (and (:query-idx params) (:col-idx params)
           (:term-id params)
           (re-matches #"\d+" (:query-idx params))
           (re-matches #"\d+" (:col-idx params))))

    :post!
    (fnk [conn version [:request [:params query-idx col-idx term-id]]]
      {:version (api/remove-query-cell! conn version (util/parse-int query-idx)
                                        (util/parse-int col-idx)
                                        term-id)})))
