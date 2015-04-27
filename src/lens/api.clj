(ns lens.api
  (:require [clojure.core.reducers :as r]
            [datomic.api :as d]
            [lens.util :refer [uuid? entity?]]
            [lens.util :as util])
  (:import [java.util.concurrent ExecutionException]))

;; ---- Single Accessors ------------------------------------------------------

(defn branch [db id]
  (d/entity db [:branch/id id]))

(defn workbook [db id]
  (d/entity db [:workbook/id id]))

(defn query [db id]
  (d/entity db [:query/id id]))

(defn user [db id]
  (d/entity db [:user/id id]))

;; ---- Lists -----------------------------------------------------------------

(defn all-branches
  "Returns a reducible coll of all branches."
  [db]
  (->> (d/datoms db :avet :branch/id)
       (r/map #(d/entity db (:e %)))))

;; ---- Traversal -------------------------------------------------------------

(defn queries
  "Returns a lazy seq of all queries of a workbook or nil."
  [workbook]
  (some-> workbook :workbook/queries util/to-seq))

(defn query-cols
  "Returns a seq of all query columns of a query sorted by rank."
  [query]
  (->> (:query-col/_query query)
       (sort-by :query-col/rank)))

(defn query-cells
  "Returns a seq of all query cells of a query column sorted by rank."
  [query]
  (->> (:query-cell/_col query)
       (sort-by :query-cell/rank)))

(defn private-workbooks [user]
  {:pre [(:user/id user)]}
  (:user/private-workbooks user))

;; ---- Creations -------------------------------------------------------------

(defn- create [conn fn]
  (let [tid (d/tempid :db.part/user)
        tx-result @(d/transact conn [(fn tid)])
        db (:db-after tx-result)]
    (d/entity db (d/resolve-tempid db (:tempids tx-result) tid))))

(defn create-standard-workbook
  "Creates a workbook entity with one query and a default of three empty query
  cols."
  [conn]
  {:post [(:workbook/id %)]}
  (let [tid (d/tempid :db.part/user)
        tx-result @(d/transact conn [[:workbook.fn/create-standard tid]])
        db (:db-after tx-result)]
    (d/entity db (d/resolve-tempid db (:tempids tx-result) tid))))

(defn create-branch
  "Creates a new branch based on the given workbook."
  [conn workbook name]
  {:pre [(:workbook/id workbook) (string? name)]
   :post [(:branch/id %)]}
  (create conn (fn [tid] [:workbook.fn/create-branch tid (:db/id workbook)
                          name])))

(defn add-query
  "Creates a new workbook which shares all queries with the old one and adds one
  new query to it. Returns the new workbook."
  [conn workbook]
  {:pre [(:workbook/id workbook)]
   :post [(:workbook/id %)]}
  (let [tid (d/tempid :db.part/user)
        tx-result @(d/transact conn [[:workbook.fn/add-query tid
                                      (:db/id workbook)]])
        db (:db-after tx-result)]
    (d/entity db (d/resolve-tempid db (:tempids tx-result) tid))))

(defn transact [conn tx-data]
  (try
    @(d/transact conn tx-data)
    (catch ExecutionException e (throw (.getCause e)))
    (catch Exception e (throw e))))

(defn update-branch!
  "Updates the branch to point to the given workbook.

  Returns the branch based on the new database. Checks that the old workbook is
  still current - throws a exception with type :lens.schema/precondition-failed
  if not. Throws :lens.schema/branch-not-found if the branch doesn't exist.
  Throws :lens.schema/workbook-not-found if the new workbook does not exist.
  Branch Id is from :branch/id and workbook ids are from :workbook/id."
  [conn branch-id old-workbook-id new-workbook-id]
  (let [r (transact conn [[:branch.fn/update branch-id old-workbook-id
                              new-workbook-id]])]
    (d/entity (:db-after r) [:branch/id branch-id])))

(defn create-private-workbook! [conn sub name]
  {:pre [(string? sub) (string? name)]
   :post [(:workbook/id %)]}
  (create conn (fn [tid] [:workbook.fn/create-private tid sub name])))
