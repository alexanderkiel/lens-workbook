(ns lens.api
  (:require [clojure.core.reducers :as r]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [datomic.api :as d]
            [lens.util :refer [uuid? entity?]]
            [lens.util :as util :refer [Nat]]
            [schema.core :as s :refer [Str Any]])
  (:import [java.util.concurrent ExecutionException]))

;; ---- Schema ----------------------------------------------------------------

(defn workbook? [x]
  (and (:workbook/id x) (:workbook/head x)))

(def Workbook
  (s/constrained Any workbook?))

(def Version
  (s/pred :version/id))

(def User
  (s/pred :user/id))

(def Term
  {:type (s/enum :form :item-group :item :code-list-item)
   :id Str})

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

(defn- create
  "Runs a transaction and returns one new entity.

  The function has to take a tempid as its only argument. The entity to be
  returned has to be created with this tempid."
  [conn fn]
  (let [tid (d/tempid :db.part/user)
        tx-result @(d/transact conn (fn tid))
        db-after (:db-after tx-result)]
    (d/entity db-after (d/resolve-tempid db-after (:tempids tx-result) tid))))

(defn transact [conn tx-data]
  (try
    @(d/transact conn tx-data)
    (catch ExecutionException e (throw (.getCause e)))
    (catch Exception e (throw e))))

(s/defn update-workbook! :- Workbook
  "Updates the workbook to point to the given version.

  Returns the workbook based on the new database. Checks in transaction that the
  head of the workbook is still current - throws a exception with type
  :lens.schema/precondition-failed if not. Throws
  :lens.schema/workbook-not-found if the workbook doesn't exist.
  Throws :lens.schema/version-not-found if the new version does not exist.
  Workbook Id is from :workbook/id and version ids are from :version/id."
  [conn workbook :- Workbook version-id :- Str]
  (let [r (transact conn [[:workbook.fn/update (:workbook/id workbook)
                           (-> workbook :workbook/head :version/id)
                           version-id]])]
    (d/entity (:db-after r) [:workbook/id (:workbook/id workbook)])))

(s/defn create-private-workbook! :- Workbook
  "Creates a new private workbook with name for the user with id.

  Creates the user if it does not exist. The new workbook will have one initial
  version with one query in it."
  [conn user-id name]
  {:pre [(string? user-id) (string? name)]
   :post [(:workbook/id %)]}
  (create conn (fn [tid] [[:workbook.fn/create-private tid user-id name]])))

(s/defn add-query :- Version
  "Returns a new version with one standard query added."
  [conn version :- Version]
  (log/debug "Add query to version" (:version/id version))
  (create conn (fn [tid] [[:version.fn/add-query tid (:db/id version)]])))

(s/defn remove-query :- Version
  "Returns a new version with the query at idx removed."
  [conn version :- Version idx :- Nat]
  (log/debug "Remove query at index" idx "from version" (:version/id version))
  (create conn (fn [tid] [[:version.fn/remove-query tid (:db/id version) idx]])))

(s/defn duplicate-query :- Version
  "Returns a new version with the query at idx duplicated. The duplicate will be
  inserted right after the query at idx."
  [conn version :- Version idx :- Nat]
  (log/debug "Duplicate query at index" idx "of version" (:version/id version))
  (create conn (fn [tid] [[:version.fn/duplicate-query tid (:db/id version) idx]])))

(s/defn add-query-cell :- Version
  "Returns a new version with a query cell added to the query and column with
  the given indicies.

  Term is a vector of type and id."
  [conn version :- Version query-idx :- Nat col-idx :- Nat term :- Term]
  (log/debug "Add query cell to version" (:version/id version))
  (create conn (fn [tid] [[:version.fn/add-query-cell tid (:db/id version)
                           query-idx col-idx term]])))

(s/defn remove-query-cell :- Version
  "Returns a new version with the query cell with term-id removed at the query
  and column with the given indicies."
  [conn version :- Version query-idx :- Nat col-idx :- Nat term-id :- Str]
  (log/debug "Remove query cell from version" (:version/id version))
  (create conn (fn [tid] [[:version.fn/remove-query-cell tid (:db/id version)
                           query-idx col-idx term-id]])))
