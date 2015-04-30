(ns lens.schema
  (:require [datomic.api :as d]))

(defmacro func [name doc params code]
  `{:db/ident ~name
    :db/doc ~doc
    :db/fn (d/function '{:lang "clojure" :requires [[shortid.core]]
                         :params ~params :code ~code})})

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

(def workbook
  "

  "
  {:attributes
   [{:db/ident :workbook/id
     :db/valueType :db.type/string
     :db/unique :db.unique/identity
     :db/cardinality :db.cardinality/one
     :db/doc "The identifier of a workbook."}

    {:db/ident :workbook/name
     :db/valueType :db.type/string
     :db/cardinality :db.cardinality/one}

    {:db/ident :workbook/head
     :db/valueType :db.type/ref
     :db/cardinality :db.cardinality/one
     :db/doc "A reference to the most recent version of the workbook."}]

   :functions
   [(func :find-unique
      ""
      [db attr id-gen]
      (first (drop-while #(d/entity db [attr %]) (repeatedly id-gen))))

    (func :workbook.fn/create-private
      "Creates a new private workbook with name for the user with id.

      Creates the user if it does not exist. The new workbook will have one initial
      version with one query in it."
      [db tid user-id name]
      [{:db/id (d/tempid :db.part/user)
        :user/id user-id
        :user/private-workbooks tid}
       {:db/id tid
        :workbook/id (d/invoke db :find-unique
                               db :workbook/id #(shortid.core/generate 5))
        :workbook/name name
        :workbook/head #db/id[:db.part/user -1]}
       {:db/id #db/id[:db.part/user -1]
        :version/id (str (d/squuid))
        :version/queries #db/id[:db.part/user -2]}
       [:linked-list.fn/cons #db/id[:db.part/user -2] #db/id[:db.part/user -3] nil]
       {:db/id #db/id[:db.part/user -3]
        :query/name "Query 1"}])

    (func :workbook.fn/update
      "Updates the version in the workbook.
      
      Checks that the old version is still current - throws a exception with
      type ::precondition-failed if not. Throws ::workbook-not-found if the
      workbook doesn't exist. Throws ::version-not-found if the new version
      does not exist. Workbook Id is from :workbook/id and version ids are from
      :version/id."
      [db workbook-id old-version-id new-version-id]
      (if-let [workbook (d/entity db [:workbook/id workbook-id])]
        (if (= old-version-id (:version/id (:workbook/head workbook)))
          (if-let [version (d/entity db [:version/id new-version-id])]
            [[:db/add (:db/id workbook) :workbook/head (:db/id version)]]
            (throw (ex-info "New version not found."
                            {:type ::version-not-found
                             :version-id new-version-id
                             :basis-t (d/basis-t db)})))
          (throw (ex-info "Old version doesn't match."
                          {:type ::precondition-failed
                           :version-id old-version-id
                           :basis-t (d/basis-t db)})))
        (throw (ex-info "Workbook not found." {:type ::workbook-not-found
                                             :workbook-id workbook-id
                                             :basis-t (d/basis-t db)}))))]})

(def version
  "Schema of a version.
  
  A version is immutable. All updating functions return always a new version
  like operations on normal immutable data structures. The :version/parent
  references the past version of a version. One can use the parent to undo a
  change in a workbook. The parent of the initial version of a workbook is nil.
  
  A version has a linked list of queries. One can use the function
  :version.fn/add-query to add a new query in front of the list."
  {:attributes
   [{:db/ident :version/id
     :db/valueType :db.type/string
     :db/unique :db.unique/identity
     :db/cardinality :db.cardinality/one
     :db/doc "The identifier of a version."}

    {:db/ident :version/parent
     :db/valueType :db.type/ref
     :db/cardinality :db.cardinality/one
     :db/doc "A reference to the optional parent of a version."}

    {:db/ident :version/queries
     :db/valueType :db.type/ref
     :db/cardinality :db.cardinality/one
     :db/doc "A reference to a linked list of all queries in a version."}

    {:db/ident :query/name
     :db/valueType :db.type/string
     :db/cardinality :db.cardinality/one
     :db/doc "The name of a query."}

    {:db/ident :query/cols
     :db/valueType :db.type/ref
     :db/cardinality :db.cardinality/one
     :db/doc "A reference to a linked list of all columns in a query."}

    {:db/ident :query.col/cells
     :db/valueType :db.type/ref
     :db/cardinality :db.cardinality/one
     :db/doc "A reference to a linked list of all cells in a query."}

    {:db/ident :query.cell.term/type
     :db/valueType :db.type/keyword
     :db/cardinality :db.cardinality/one
     :db/doc (str "The type of a query cell. One of :form, :item-group, :item "
                  "and :code-list-item.")}

    {:db/ident :query.cell.term/id
     :db/valueType :db.type/string
     :db/cardinality :db.cardinality/one
     :db/doc (str "The id of a query cell which is the external id of the "
                  "entity representated by its type. For type :form its the "
                  "form id from lens-warehouse. Its the same id as used in "
                  ":expr of form :lens/query.")}]

   :functions
   [(func :version.fn/add-query
      "Adds a new query to a copy of the given version. Needs a tempid for the
      new version."
      [db tid version query-name]
      (let [list-tid (d/tempid :db.part/user)
            query-tid (d/tempid :db.part/user)
            queries (:version/queries (d/entity db version))]
        [{:db/id query-tid
          :query/name query-name}
         [:linked-list.fn/cons list-tid query-tid (:db/id queries)]
         {:db/id tid
          :version/id (str (d/squuid))
          :version/parent version
          :version/queries list-tid}]))
    (func :version.fn/add-query-cell
      "Adds a new query cell to a copy of the given version.

      Term is a vector of type and id.

      Needs a tempid for the new version."
      [db tid version query-idx col-idx term]
      (let [queries (:version/queries (d/entity db version))
            query (:linked-list/head queries)
            col (:linked-list/head (:query/cols query))]
        (println "query" query)
        (println "col" col)
        [{:db/id tid
          :version/id (str (d/squuid))
          :version/parent version
          :version/queries #db/id[:db.part/user -1]}

         ;; Query
         {:db/id #db/id[:db.part/user -2]
          :query/name (:query/name query)
          :query/cols #db/id[:db.part/user -3]}
         [:linked-list.fn/cons #db/id[:db.part/user -1] #db/id[:db.part/user -2]
          (:db/id (:linked-list/tail queries))]

         ;; Col
         {:db/id #db/id[:db.part/user -4]
          :query.col/cells #db/id[:db.part/user -5]}
         [:linked-list.fn/cons #db/id[:db.part/user -3] #db/id[:db.part/user -4]
          (:db/id (:linked-list/tail (:query/cols query)))]

         ;;Cell
         {:db/id #db/id[:db.part/user -6]
          :query.cell.term/type (first term)
          :query.cell.term/id (second term)}
         [:linked-list.fn/cons #db/id[:db.part/user -5] #db/id[:db.part/user -6]
          (:db/id (:query.col/cells col))]]))]})

(def user
  {:attributes
   [{:db/ident :user/id
     :db/valueType :db.type/string
     :db/unique :db.unique/identity
     :db/cardinality :db.cardinality/one
     :db/doc "The identifier of a user."}

    {:db/ident :user/private-workbooks
     :db/valueType :db.type/ref
     :db/cardinality :db.cardinality/many
     :db/doc "A reference to all private workbooks of a user."}]})

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

(defn prepare-schema []
  (-> (mapv make-attr (concat (:attributes linked-list)
                              (:attributes workbook)
                              (:attributes version)
                              (:attributes user)))
      (into (map make-func (concat (:functions linked-list)
                                   (:functions workbook)
                                   (:functions version))))))

(defn load-schema
  "Loads the schema in one transaction and derefs the result."
  [conn]
  (->> (prepare-schema)
       (d/transact conn)
       (deref)))
