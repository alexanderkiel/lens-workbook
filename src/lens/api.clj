(ns lens.api
  (:require [clojure.core.reducers :as r]
            [clojure.string :as str]
            [datomic.api :as d]
            [lens.util :refer [uuid? entity?]]
            [lens.util :as util])
  (:import [java.util.concurrent ExecutionException]))

;; ---- Single Accessors ------------------------------------------------------

(defn workbook [db id]
  (d/entity db [:workbook/id id]))

(defn version [db id]
  (d/entity db [:version/id id]))

(defn user [db id]
  (d/entity db [:user/id id]))

;; ---- Lists -------------------------------------------------------------

(defn- all-of [db attr]
  (->> (d/datoms db :avet attr)
       (r/map #(d/entity db (:e %)))))

(defn all-users [db]
  (all-of db :user/id))

(defn all-versions [db]
  (all-of db :version/id))

(defn all-queries [db]
  (->> (d/datoms db :aevt :query/cols)
       (r/map #(d/entity db (:e %)))))

;; ---- Traversal -------------------------------------------------------------

(defn queries
  "Returns a lazy seq of all queries of a version or nil."
  [version]
  (some-> version :version/queries util/to-seq))

(defn query-cols
  "Returns a lazy seq of all query columns of a query or nil."
  [query]
  (some-> query :query/cols util/to-seq))

(defn query-cells
  "Returns a lazy seq of all query cells of a query column or nil."
  [col]
  (some-> col :query.col/cells util/to-seq reverse))

(defn private-workbooks
  "Returns all private worksbooks of the user sorted by name."
  [user]
  {:pre [(:user/id user)]}
  (->> (:user/private-workbooks user)
       (sort-by (comp str/lower-case :workbook/name))))

;; ---- Creations -------------------------------------------------------------

(defn create [conn fn]
  (let [tid (d/tempid :db.part/user)
        tx-result @(d/transact conn (fn tid))
        db (:db-after tx-result)]
    (d/entity db (d/resolve-tempid db (:tempids tx-result) tid))))

(defn transact [conn tx-data]
  (try
    @(d/transact conn tx-data)
    (catch ExecutionException e (throw (.getCause e)))
    (catch Exception e (throw e))))

(defn update-workbook!
  "Updates the workbook to point to the given version.

  Returns the workbook based on the new database. Checks that the old version is
  still current - throws a exception with type :lens.schema/precondition-failed
  if not. Throws :lens.schema/workbook-not-found if the workbook doesn't exist.
  Throws :lens.schema/version-not-found if the new version does not exist.
  Workbook Id is from :workbook/id and version ids are from :version/id."
  [conn workbook-id old-version-id new-version-id]
  (let [r (transact conn [[:workbook.fn/update workbook-id old-version-id
                           new-version-id]])]
    (d/entity (:db-after r) [:workbook/id workbook-id])))

(defn create-private-workbook!
  "Creates a new private workbook with name for the user with id.

  Creates the user if it does not exist. The new workbook will have one initial
  version with one query in it."
  [conn user-id name]
  {:pre [(string? user-id) (string? name)]
   :post [(:workbook/id %)]}
  (create conn (fn [tid] [[:workbook.fn/create-private tid user-id name]])))

(defn add-query!
  "Adds a new query to a copy of the given version."
  [conn version]
  {:pre [(:version/id version)]
   :post [(:version/id %)]}
  (create conn (fn [tid] [[:version.fn/add-query tid (:db/id version)]])))

(defn remove-query!
  "Removes the query at idx from a copy of the given version."
  [conn version idx]
  {:pre [(:version/id version) (not (neg? idx))]
   :post [(:version/id %)]}
  (create conn (fn [tid] [[:version.fn/remove-query tid (:db/id version) idx]])))

(defn add-query-cell!
  "Adds a new query cell to a copy of the given version.

  Term is a vector of type and id."
  [conn version query-idx col-idx term]
  {:pre [(:version/id version) (not (neg? query-idx)) (not (neg? col-idx))
         (vector? term)]
   :post [(:version/id %)]}
  (create conn (fn [tid] [[:version.fn/add-query-cell tid (:db/id version)
                           query-idx col-idx term]])))

(defn remove-query-cell!
  "Removes a query cell from a copy of the given version."
  [conn version query-idx col-idx term-id]
  {:pre [(:version/id version) (not (neg? query-idx)) (not (neg? col-idx))
         (string? term-id)]
   :post [(:version/id %)]}
  (create conn (fn [tid] [[:version.fn/remove-query-cell tid (:db/id version)
                           query-idx col-idx term-id]])))
