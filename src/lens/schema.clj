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

  A single element list consists of a entity with one :l/head
  attribute pointing to the single element with has to be a entity itself.
  Primitive lists are not supported.

  A list with more than one element contains the additional attribute
  :l/tail which points to another list entity containing one or more
  elements recursively."
  {:attributes
   [{:db/ident :l/head
     :db/valueType :db.type/ref
     :db/cardinality :db.cardinality/one
     :db/doc "A reference to the head element of a linked list."}

    {:db/ident :l/tail
     :db/valueType :db.type/ref
     :db/cardinality :db.cardinality/one
     :db/doc "A reference to the tail list of a linked list."}]

   :functions
   [(func :l.fn/cons
      "Cons function for linked lists. Tid is the tempid of the new list
      (cons cell)."
      [_ tid head tail]
      [(let [e {:db/id tid :l/head head}]
         (if tail
           (assoc e :l/tail tail)
           e))])

    (func :l.fn/insert
      "Inserts x into list at index idx. Inserting at index 0 is the same as
      cons. List has to exist already in the database because it will be
      traversed if needed. Tid is the tempid of the new list."
      [db tid list idx x]
      (if (zero? idx)
        [[:l.fn/cons tid x list]]
        (let [tid-2 (d/tempid :db.part/user)
              list (d/entity db list)]
          [[:l.fn/cons tid (:db/id (:l/head list)) tid-2]
           [:l.fn/insert tid-2 (:db/id (:l/tail list)) (dec idx) x]])))

    (func :l.fn/append
      "Appends x to the end of the list. O(n) operation."
      [db tid list x]
      (if list
        (let [tid-2 (d/tempid :db.part/user)
              list (d/entity db list)]
          [[:l.fn/cons tid (:db/id (:l/head list)) tid-2]
           [:l.fn/append tid-2 (:db/id (:l/tail list)) x]])
        [[:l.fn/cons tid x nil]]))

    (func :l.fn/update
      "Updates the list element at index idx with x. List has to exist already
      in the database because it will be traversed if needed. Tid is the tempid
      of the new list."
      [db tid list idx x]
      (if (zero? idx)
        [[:l.fn/cons tid x (:db/id (:l/tail (d/entity db list)))]]
        (let [tid-2 (d/tempid :db.part/user)
              list (d/entity db list)]
          [[:l.fn/cons tid (:db/id (:l/head list)) tid-2]
           [:l.fn/update tid-2 (:db/id (:l/tail list)) (dec idx) x]])))]})

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

    (func :workbook.fn/create
      ""
      [db tid name head]
      (let [id-gen #(shortid.core/generate 5)]
        [{:db/id tid
          :workbook/id (d/invoke db :find-unique db :workbook/id id-gen)
          :workbook/name name
          :workbook/head head}]))

    (func :workbook.fn/create-private
      "Creates a new private workbook with name for the user with id.

      Creates the user if it does not exist. The new workbook will have one initial
      version with one query in it."
      [db tid user-id name]
      (let [user #db/id[:db.part/user]
            version #db/id[:db.part/user]
            queries #db/id[:db.part/user]
            query #db/id[:db.part/user]]
        [[:user.fn/upsert user user-id]
         [:db/add user :user/private-workbooks tid]
         [:workbook.fn/create tid name version]
         [:version.fn/create-initial version queries]

         [:query.fn/create query]
         [:l.fn/cons queries query nil]]))

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
     :db/doc "A reference to a linked list of all queries in a version."}]

   :functions
   [(func :version.fn/create-initial
      ""
      [_ tid queries]
      [{:db/id tid
        :version/id (str (d/squuid))
        :version/queries queries}])

    (func :version.fn/create
      ""
      [_ tid parent queries]
      [{:db/id tid
        :version/id (str (d/squuid))
        :version/parent parent
        :version/queries queries}])

    (func :version.fn/create-empty
      ""
      [_ tid parent]
      [{:db/id tid
        :version/id (str (d/squuid))
        :version/parent parent}])

    (func :version.fn/add-query
      "Adds a new query to a copy of the given version. Needs a tempid for the
      new version."
      [db tid version]
      (let [queries (:version/queries (d/entity db version))
            new-queries #db/id[:db.part/user]
            query #db/id[:db.part/user]]
        [[:version.fn/create tid version new-queries]
         [:query.fn/create query]
         [:l.fn/append new-queries (:db/id queries) query]]))

    (func :version.fn/remove-query
      "Removes the query at idx from a copy of the given version. Needs a tempid
      for the new version."
      [db tid version idx]
      (let [seq (fn seq [l] (when l (cons (:l/head l) (seq (:l/tail l)))))

            queries (:version/queries (d/entity db version))

            query-seq (seq queries)
            new-query-seq (concat (take idx query-seq)
                                  (drop (inc idx) query-seq))]
        (if (empty? new-query-seq)
          [[:version.fn/create-empty tid version]]
          (loop [q-tid #db/id[:db.part/user]
                 qs new-query-seq
                 tx-data [[:version.fn/create tid version q-tid]]]
            (if (next qs)
              (let [n-tid (d/tempid :db.part/user)]
                (recur
                  n-tid
                  (rest qs)
                  (conj tx-data [:l.fn/cons q-tid (:db/id (first qs)) n-tid])))
              (conj tx-data [:l.fn/cons q-tid (:db/id (first qs)) nil]))))))

    (func :version.fn/add-query-cell
      "Adds a new query cell to a copy of the given version.

      Term is a vector of type and id.

      Needs a tempid for the new version."
      [db tid version query-idx col-idx term]
      (let [seq (fn seq [l] (when l (cons (:l/head l) (seq (:l/tail l)))))

            queries (:version/queries (d/entity db version))
            query (nth (seq queries) query-idx)

            cols (:query/cols query)
            col (nth (seq cols) col-idx)

            new-queries #db/id[:db.part/user]
            new-query #db/id[:db.part/user]
            new-cols #db/id[:db.part/user]
            new-col #db/id[:db.part/user]
            new-cells #db/id[:db.part/user]
            new-cell #db/id[:db.part/user]]

        [[:version.fn/create tid version new-queries]

         ;; Create a new query which holds the new col list
         {:db/id new-query :query/cols new-cols}
         [:l.fn/update new-queries (:db/id queries) query-idx new-query]

         ;; Create a new col which holds the new cell list
         {:db/id new-col :query.col/cells new-cells}
         [:l.fn/update new-cols (:db/id cols) col-idx new-col]

         ;; Add a new cell in front of all cells of the col
         {:db/id new-cell
          :query.cell.term/type (first term)
          :query.cell.term/id (second term)}
         [:l.fn/cons new-cells new-cell (:db/id (:query.col/cells col))]]))

    (func :version.fn/remove-query-cell
      "Removes a query cell from a copy of the given version.
      
      Needs a tempid for the new version."
      [db tid version query-idx col-idx term-id]
      (let [seq (fn seq [l] (when l (cons (:l/head l) (seq (:l/tail l)))))

            queries (:version/queries (d/entity db version))
            query (nth (seq queries) query-idx)

            cols (:query/cols query)
            col (nth (seq cols) col-idx)

            new-cell-seq (->> (seq (:query.col/cells col))
                              (remove #(= term-id (:query.cell.term/id %))))

            new-queries #db/id[:db.part/user]
            new-query #db/id[:db.part/user]
            new-cols #db/id[:db.part/user]
            new-col #db/id[:db.part/user]]

        (into
          [[:version.fn/create tid version new-queries]

           ;; Create a new query which holds the new col list
           {:db/id new-query :query/cols new-cols}
           [:l.fn/update new-queries (:db/id queries) query-idx new-query]

           ;; Create a new col which holds the new cell list
           [:l.fn/update new-cols (:db/id cols) col-idx new-col]]
          (if (empty? new-cell-seq)
            [[:query.col.fn/create new-col]]
            (loop [tid #db/id[:db.part/user]
                   cells new-cell-seq
                   tx-data [{:db/id new-col :query.col/cells tid}]]
              (if (next cells)
                (let [next-tid (d/tempid :db.part/user)]
                  (recur
                    next-tid
                    (rest cells)
                    (conj tx-data [:l.fn/cons tid (:db/id (first cells))
                                   next-tid])))
                (conj tx-data [:l.fn/cons tid (:db/id (first cells)) nil])))))))]})

(def query
  {:attributes
   [{:db/ident :query/name
     :db/valueType :db.type/string
     :db/cardinality :db.cardinality/one
     :db/doc "The name of a query."}

    {:db/ident :query/cols
     :db/valueType :db.type/ref
     :db/cardinality :db.cardinality/one
     :db/doc "A reference to a linked list of all columns in a query."}

    {:db/ident :query.col/dummy
     :db/valueType :db.type/keyword
     :db/cardinality :db.cardinality/one
     :db/doc "A dummy element for empty query columns. It is needed because
         empty query columns have no attributes."}

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
   [(func :query.fn/create
      ""
      [_ tid]
      (let [cols #db/id[:db.part/user]
            cols-t1 #db/id[:db.part/user]
            cols-t2 #db/id[:db.part/user]
            col-1 #db/id[:db.part/user]
            col-2 #db/id[:db.part/user]
            col-3 #db/id[:db.part/user]]
        [{:db/id tid :query/cols cols}
         [:query.col.fn/create col-1]
         [:query.col.fn/create col-2]
         [:query.col.fn/create col-3]
         [:l.fn/cons cols col-1 cols-t1]
         [:l.fn/cons cols-t1 col-2 cols-t2]
         [:l.fn/cons cols-t2 col-3 nil]]))

    (func :query.col.fn/create
      ""
      [_ tid]
      [{:db/id tid :query.col/dummy :dummy}])]})

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
     :db/doc "A reference to all private workbooks of a user."}]
   :functions
   [(func :user.fn/upsert
      ""
      [_ tid user-id]
      [{:db/id tid
        :user/id user-id}])]})

(defn- assoc-tempid [m partition]
  (assoc m :db/id (d/tempid partition)))

(defn make-attr
  "Assocs :db/id and :db.install/_attribute to the attr map."
  [attr]
  (-> (assoc-tempid attr :db.part/db)
      (assoc :db.install/_attribute :db.part/db)))

(defn make-enum
  "Assocs :db/id to the enum map."
  [enum]
  (assoc-tempid {:db/ident enum} :db.part/user))

(defn make-func
  "Assocs :db/id to the func map."
  [func]
  (assoc-tempid func :db.part/user))

(defn prepare-schema []
  (-> (mapv make-attr (concat (:attributes linked-list)
                              (:attributes workbook)
                              (:attributes version)
                              (:attributes query)
                              (:attributes user)))
      (into (map make-enum (:enums linked-list)))
      (into (map make-func (concat (:functions linked-list)
                                   (:functions workbook)
                                   (:functions version)
                                   (:functions query)
                                   (:functions user))))))

(defn load-schema
  "Loads the schema in one transaction and derefs the result."
  [conn]
  (->> (prepare-schema)
       (d/transact conn)
       (deref)))
