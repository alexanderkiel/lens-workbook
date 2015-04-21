(ns lens.schema
  (:require [datomic.api :as d]))

(defmacro func [name doc params code]
  `{:db/ident ~name
    :db/doc ~doc
    :db/fn (d/function '{:lang "clojure" :params ~params :code ~code})})

(def linked-list
  "Schema of a linked list.

  A empty list has no representation.

  A single element list consists of a entity with one :linked-list/head
  attribute pointing to the single element with has to be a entity itself.
  Primitive lists are not supported.

  A list with more than one element contains the additional attribute
  :linked-list/tail which points to another list entity containing one or more
  elements recursively."
  {:attributes
   [{:db/ident :linked-list/head
     :db/valueType :db.type/ref
     :db/cardinality :db.cardinality/one
     :db/doc "A reference to the head element of a linked list."}

    {:db/ident :linked-list/tail
     :db/valueType :db.type/ref
     :db/cardinality :db.cardinality/one
     :db/doc "A reference to the tail list of a linked list."}]

   :functions
   [(func :linked-list.fn/cons
      "Cons function for linked lists. Tid is the tempid of the new list
      (cons cell)."
      [_ tid head tail]
      [(let [e {:db/id tid :linked-list/head head}]
         (if tail
           (assoc e :linked-list/tail tail)
           e))])]})

(def branch
  [{:db/ident :branch/id
    :db/valueType :db.type/string
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one
    :db/doc "The identifier of a branch."}

   {:db/ident :branch/workbook
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "A reference to the workbook of a branch."}])

(def workbook
  "Schema of a workbook.

  A workbook is immutable. All updating functions return always a new workbook
  like operations on normal immutable data structures. The :workbook/parent
  references the past version of a workbook. One can use the parent to undo a
  change in a branch. The parent of a newly created workbook using the function
  :workbook.fn/create is nil.

  A workbook has a linked list of queries. One can use the function
  :workbook.fn/add-query to add a new query in front of the list."
  {:attributes
   [{:db/ident :workbook/id
     :db/valueType :db.type/string
     :db/unique :db.unique/identity
     :db/cardinality :db.cardinality/one
     :db/doc "The identifier of a workbook."}

    {:db/ident :workbook/parent
     :db/valueType :db.type/ref
     :db/cardinality :db.cardinality/one
     :db/doc "A reference to the parent workbook of the workbook."}

    {:db/ident :workbook/queries
     :db/valueType :db.type/ref
     :db/cardinality :db.cardinality/one
     :db/doc "A reference to a linked list of all queries in the workbook."}]

   :functions
   [(func :workbook.fn/create
      "Creates a workbook entity. Takes a tempid for the workbook."
      [_ tid]
      [{:db/id tid :workbook/id (str (d/squuid))}])

    (func :workbook.fn/create-branch
      "Creates branch entity based on the given workbook.

      Takes a tempid for the branch."
      [db tid workbook]
      [{:db/id tid :branch/id (str (d/squuid)) :branch/workbook workbook}])

    (func :workbook.fn/add-query
      "Adds a new query entity to a copy of the given workbook. Needs a tempid
      for the new workbook."
      [db tid workbook]
      (let [list-tid (d/tempid :db.part/user)
            query-tid (d/tempid :db.part/user)
            queries (:workbook/queries (d/entity db workbook))]
        [{:db/id query-tid
          :query/id (str (d/squuid))}
         [:linked-list.fn/cons list-tid query-tid (:db/id queries)]
         {:db/id tid
          :workbook/id (str (d/squuid))
          :workbook/parent workbook
          :workbook/queries list-tid}]))

    (func :workbook.fn/create-standard
      "Creates a workbook entity with one query and a default of three empty
      query cols. Takes a tempid for the workbook."
      [db tid]
      (let [wb-tid (d/tempid :db.part/user)]
        [[:workbook.fn/create wb-tid]
         [:workbook.fn/add-query tid wb-tid]]))]})

(def base-schema
  {:attributes
   [{:db/ident :query/id
     :db/valueType :db.type/string
     :db/unique :db.unique/identity
     :db/cardinality :db.cardinality/one
     :db/doc "The identifier of a query."}

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
   [{:db/ident :add-branch
     :db/doc (str "Adds a branch entity. Needs a tempid and a reference to its "
                  "workbook.")
     :db/fn
     (d/function
       '{:lang "clojure"
         :params [_ tid workbook]
         :code [{:db/id tid
                 :branch/id (str (d/squuid))
                 :branch/workbook workbook}]})}

    {:db/ident :add-query-col
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

    {:db/ident :add-query-cell
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

    {:db/ident :retract-query-cell
     :db/fn
     (d/function
       '{:lang "clojure"
         :params [_ cell]
         :code [[:db.fn/retractEntity cell]]})}]})

(defn- assoc-tempid [m partition]
  (assoc m :db/id (d/tempid partition)))

(defn make-attr
  "Assocs :db/id and :db.install/_attribute to the attr map."
  [attr]
  (-> (assoc-tempid attr :db.part/db)
      (assoc :db.install/_attribute :db.part/db)))

(defn make-func
  "Assocs :db/id to the func map."
  [func]
  (assoc-tempid func :db.part/user))

(defn prepare-schema [schema]
  (-> (mapv make-attr (concat (:attributes linked-list)
                              branch
                              (:attributes workbook)
                              (:attributes schema)))
      (into (map make-func (concat (:functions linked-list)
                                   (:functions workbook)
                                   (:functions schema))))))

(defn load-schema
  "Loads the schema in one transaction and derefs the result."
  [conn]
  (->> (prepare-schema base-schema)
       (d/transact conn)
       (deref)))
