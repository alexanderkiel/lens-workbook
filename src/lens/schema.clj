(ns lens.schema
  (:require [datomic.api :as d]))

(def base-schema
  {:attributes
   [{:db/ident :workbook/id
     :db/valueType :db.type/uuid
     :db/unique :db.unique/identity
     :db/cardinality :db.cardinality/one
     :db/doc "The identifier of a workbook."}

    {:db/ident :query/id
     :db/valueType :db.type/uuid
     :db/unique :db.unique/identity
     :db/cardinality :db.cardinality/one
     :db/doc "The identifier of a query."}

    {:db/ident :query/workbook
     :db/valueType :db.type/ref
     :db/cardinality :db.cardinality/one
     :db/doc "A reference to the workbook of a query."}

    {:db/ident :query/rank
     :db/valueType :db.type/long
     :db/cardinality :db.cardinality/one
     :db/doc "The rank defines the ordering of queries within there workbook."}

    {:db/ident :query-col/id
     :db/valueType :db.type/uuid
     :db/unique :db.unique/identity
     :db/cardinality :db.cardinality/one
     :db/doc "The identifier of a query column."}

    {:db/ident :query-col/query
     :db/valueType :db.type/ref
     :db/cardinality :db.cardinality/one
     :db/doc "A reference to the query of a query column."}

    {:db/ident :query-col/rank
     :db/valueType :db.type/long
     :db/cardinality :db.cardinality/one
     :db/doc "The rank defines the ordering of query columns within there query."}

    {:db/ident :query-cell/id
     :db/valueType :db.type/uuid
     :db/unique :db.unique/identity
     :db/cardinality :db.cardinality/one
     :db/doc "The identifier of a query cell."}

    {:db/ident :query-cell/col
     :db/valueType :db.type/ref
     :db/cardinality :db.cardinality/one
     :db/doc "A reference to the query column of a query cell."}

    {:db/ident :query-cell/rank
     :db/valueType :db.type/long
     :db/cardinality :db.cardinality/one
     :db/doc "The rank defines the ordering of query cells within there column."}

    {:db/ident :query-cell.term/type
     :db/valueType :db.type/keyword
     :db/cardinality :db.cardinality/one
     :db/doc (str "The type of a query cell. One of :form, :item-group, :item "
                  "and :code-list-item.")}

    {:db/ident :query-cell.term/id
     :db/valueType :db.type/keyword
     :db/cardinality :db.cardinality/one
     :db/doc (str "The id of a query cell which is the external id of the "
                  "entity representated by its type. For type :form its the "
                  "form id from lens-warehouse. Its the same id as used in "
                  ":expr of form :lens/query.")}]
   :functions
   [{:db/id (d/tempid :db.part/user)
     :db/ident :add-workbook
     :db/doc "Adds a workbook entity. Needs a tempid."
     :db/fn
     (d/function
       '{:lang "clojure"
         :params [_ tid id]
         :code [{:db/id tid :workbook/id (d/squuid)}]})}
    {:db/id (d/tempid :db.part/user)
     :db/ident :add-query
     :db/doc (str "Adds a query entity. Needs a tempid and a reference to its "
                  "workbook. Chooses the next available query rank.")
     :db/fn
     (d/function
       '{:lang "clojure"
         :params [db tid workbook]
         :code (let [rank (-> (d/q '[:find (max ?r) .
                                     :in $ ?w
                                     :where
                                     [?q :query/workbook ?w]
                                     [?q :query/rank ?r]]
                                   db workbook)
                              (or 0))]
                 [{:db/id tid
                   :query/id (d/squuid)
                   :query/workbook workbook
                   :query/rank (inc rank)}])})}
    {:db/id (d/tempid :db.part/user)
     :db/ident :add-query-col
     :db/doc (str "Adds a query column entity. Needs a tempid and a reference "
                  "to its query. Chooses the next available query column rank.")
     :db/fn
     (d/function
       '{:lang "clojure"
         :params [db tid query]
         :code (let [rank (-> (d/q '[:find (max ?r) .
                                     :in $ ?q
                                     :where
                                     [?c :query-col/query ?q]
                                     [?c :query-col/rank ?r]]
                                   db query)
                              (or 0))]
                 [{:db/id tid
                   :query-col/id (d/squuid)
                   :query-col/query query
                   :query-col/rank (inc rank)}])})}

    {:db/id (d/tempid :db.part/user)
     :db/ident :add-query-cell
     :db/fn
     (d/function
       '{:lang "clojure"
         :params [db col type id]
         :code (let [rank (-> (d/q '[:find (max ?r) .
                                     :in $ ?col
                                     :where
                                     [?c :query-cell/col ?col]
                                     [?c :query-cell/rank ?r]]
                                   db col)
                              (or 0))]
                 [{:db/id (d/tempid :db.part/user)
                   :query-cell/col col
                   :query-cell/rank (inc rank)
                   :query-cell/type type
                   :query-cell/id id}])})}

    {:db/id (d/tempid :db.part/user)
     :db/ident :add-standard-workbook
     :db/doc (str "Adds a new workbook with one query and a default of three "
                  "empty query cols.")
     :db/fn
     (d/function
       '{:lang "clojure"
         :params [db tid]
         :code (let [query-tid (d/tempid :db.part/user)]
                 [[:add-workbook tid (d/squuid)]
                  [:add-query query-tid tid]
                  [:add-query-col (d/tempid :db.part/user) query-tid]
                  [:add-query-col (d/tempid :db.part/user) query-tid]
                  [:add-query-col (d/tempid :db.part/user) query-tid]])})}

    {:db/id (d/tempid :db.part/user)
     :db/ident :retract-query-cell
     :db/fn
     (d/function
       '{:lang "clojure"
         :params [_ cell]
         :code [[:db.fn/retractEntity cell]]})}]})

(defn- assoc-db-id [m]
  (assoc m :db/id (d/tempid :db.part/db)))

(defn make-attr
  "Assocs :db/id and :db.install/_attribute to the attr map."
  [attr]
  (-> (assoc-db-id attr)
      (assoc :db.install/_attribute :db.part/db)))

(defn prepare-schema [schema]
  (-> (mapv make-attr (:attributes schema))
      (into (:functions schema))))

(defn load-schema
  "Loads the schema in one transaction and derefs the result."
  [conn]
  (->> (prepare-schema base-schema)
       (d/transact conn)
       (deref)))
