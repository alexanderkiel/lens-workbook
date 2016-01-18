(ns lens.handler.version
  (:use plumbing.core)
  (:require [liberator.core :refer [resource]]
            [liberator.representation :refer [as-response]]
            [lens.handler.util :as hu]
            [lens.api :as api]
            [lens.util :as util :refer [Nat]]
            [schema.core :refer [Int Str Any]]))

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

(defn path [path-for version]
  (path-for :version-handler :id (:version/id version)))

(defn assoc-parent-link [links path-for version]
  (if-let [parent (:version/parent version)]
    (assoc links :lens/parent {:href (path path-for parent)})
    links))

(defn add-query-form [path-for version]
  {:href (path-for :add-query-handler :id (:version/id version))
   :label "Add Query"
   :desc "Creates a new version with one query added."})

(defn remove-query-form [path-for version]
  {:href (path-for :remove-query-handler :id (:version/id version))
   :label "Remove Query"
   :desc "Creates a new version with the query at idx removed."
   :params
   {:idx
    {:type Int
     :label "Index"
     :desc "The index of the query in the list of queries."}}})

(defn duplicate-query-form [path-for version]
  {:href (path-for :duplicate-query-handler :id (:version/id version))
   :label "Duplicate Query"
   :desc "Creates a new version with the query at idx duplicated."
   :params
   {:idx
    {:type Int
     :label "Index"
     :desc "The index of the query in the list of queries."}}})

(defn add-query-cell-form [path-for version]
  {:href (path-for :add-query-cell-handler :id (:version/id version))
   :label "Add Query Cell"
   :desc "Creates a new version with a query cell added."
   :params
   {:query-idx
    {:type Int
     :label "Query Index"
     :desc "The index of the query in the list of queries."}
    :col-idx
    {:type Int
     :label "Column Index"
     :desc "The index of the column in the list of columns of the query identified by query-idx."}
    :term-type
    {:type (:type api/Term)
     :label "Term Type"
     :desc "The type of the term (like form or item)."}
    :term-id
    {:type Str
     :label "Term Id"
     :desc "The id of the term (like T00001 or T00001_F0001)."}}})

(defn remove-query-cell-form [path-for version]
  {:href (path-for :remove-query-cell-handler :id (:version/id version))
   :label "Remove Query Cell"
   :desc "Creates a new version with a query cell removed."
   :params
   {:query-idx
    {:type Int
     :label "Query Index"
     :desc "The index of the query in the list of queries."}
    :col-idx
    {:type Int
     :label "Column Index"
     :desc "The index of the column in the list of columns of the query identified by query-idx."}
    :term-id
    {:type Str
     :label "Term Id"
     :desc "The id of the term (like T00001 or T00001_F0001)."}}})

(defnk render [version [:request path-for]]
  {:data
   {:id (:version/id version)
    :queries (->> (api/queries version)
                  (mapv #(render-query %)))}
   :links
   (-> {:up {:href (path-for :service-document-handler)}
        :self {:href (path path-for version)}}
       (assoc-parent-link path-for version))
   :forms
   {:lens/add-query (add-query-form path-for version)
    :lens/remove-query (remove-query-form path-for version)
    :lens/duplicate-query (duplicate-query-form path-for version)
    :lens/add-query-cell (add-query-cell-form path-for version)
    :lens/remove-query-cell (remove-query-cell-form path-for version)}})

(def handler
  (resource
    (hu/resource-defaults :cache-control "max-age=86400")

    :service-available? hu/db-available?

    :exists?
    (fnk [db [:request [:params id]]]
      (when-let [version (api/version db id)]
        {:version version}))

    :etag (hu/etag 1)

    :handle-ok render

    :handle-not-found
    (fnk [[:request path-for]]
      (hu/error-body path-for "Version not found."))

    :location
    (fnk [[:request path-for]]
      (fnk [version] (path path-for version)))))

(defn post-resource-defaults []
  (merge
    (hu/resource-defaults)

    {:allowed-methods [:post]
     :can-post-to-missing? false
     :respond-with-entity? true

     :service-available? hu/db-available?

     :exists?
     (fnk [db [:request [:params id]]]
       (when-let [version (api/version db id)]
         {:version version}))

     :location
     (fnk [version [:request path-for]] (path path-for version))

     :handle-exception
     (fnk [exception [:request path-for]]
       (if (= :conflict (:type (ex-data exception)))
         (hu/error path-for 409 (.getMessage exception))
         (throw exception)))}))

(def add-query-handler
  (resource
    (post-resource-defaults)

    :post!
    (fnk [conn version]
      {:version (api/add-query conn version)})))

(defn- query-index-out-of-bounds [idx num-queries]
  (ex-info (str "Index " idx " out of bounds. Number of queries is "
                num-queries ".") {:type :conflict}))

(defn- check-query-bounds [version query-idx]
  (let [num-queries (count (util/to-seq (:version/queries version)))]
    (when-not (< query-idx num-queries)
      (throw (query-index-out-of-bounds query-idx num-queries)))))

(defn- col-index-out-of-bounds [idx num-cols]
  (ex-info (str "Index " idx " out of bounds. Number of columns is "
                num-cols ".") {:type :conflict}))

(defn- check-col-bounds [query col-idx]
  (let [num-cols (count (api/query-cols query))]
    (when-not (< col-idx num-cols)
      (throw (col-index-out-of-bounds col-idx num-cols)))))

(def remove-query-handler
  (resource
    (post-resource-defaults)

    :processable? (hu/validate-params {:id Str :idx Nat Any Any})

    :post!
    (fnk [conn version [:request [:params idx]]]
      (check-query-bounds version idx)
      {:version (api/remove-query conn version idx)})))

(def duplicate-query-handler
  (resource
    (post-resource-defaults)

    :processable? (hu/validate-params {:id Str :idx Nat Any Any})

    :post!
    (fnk [conn version [:request [:params idx]]]
      (check-query-bounds version idx)
      {:version (api/duplicate-query conn version idx)})))

(def add-query-cell-handler
  (resource
    (post-resource-defaults)

    :processable?
    (hu/validate-params
      {:id Str
       :query-idx Nat
       :col-idx Nat
       :term-type (:type api/Term)
       :term-id (:id api/Term)
       Any Any})

    :post!
    (fnk [conn version [:request [:params query-idx col-idx term-type term-id]]]
      (check-query-bounds version query-idx)
      (check-col-bounds (nth (api/queries version) query-idx) col-idx)
      {:version
       (api/add-query-cell conn version query-idx col-idx
                           {:type term-type :id term-id})})))

(def remove-query-cell-handler
  (resource
    (post-resource-defaults)

    :processable?
    (hu/validate-params
      {:id Str
       :query-idx Nat
       :col-idx Nat
       :term-id (:id api/Term)
       Any Any})

    :post!
    (fnk [conn version [:request [:params query-idx col-idx term-id]]]
      (check-query-bounds version query-idx)
      (check-col-bounds (nth (api/queries version) query-idx) col-idx)
      {:version (api/remove-query-cell conn version query-idx col-idx
                                       term-id)})))
