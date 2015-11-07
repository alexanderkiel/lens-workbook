(ns lens.schema-test
  (:require [clojure.test :refer :all]
            [lens.schema :refer :all]
            [datomic.api :as d]
            [lens.util :refer [to-seq]]))

(defn database-fixture [f]
  (d/create-database "datomic:mem:test")
  (let [conn (d/connect "datomic:mem:test")]
    (load-schema conn)
    (f))
  (d/delete-database "datomic:mem:test"))

(use-fixtures :once database-fixture)

(defn- db [] (d/db (d/connect "datomic:mem:test")))

(defn- tid [n]
  (d/tempid :db.part/user n))

(defn- with
  ([tx-data] (with (db) tx-data))
  ([db tx-data]
   (d/with db tx-data)))

(defn- resolve-tid [res tid]
  (let [db (:db-after res)]
    (d/entity db (d/resolve-tempid db (:tempids res) tid))))

(deftest cons-test
  (testing "single cons cell"
    (let [res (with [{:db/id (tid -2) :query/name "Q1"}
                     [:l.fn/cons (tid -1) (tid -2) nil]])
          list (resolve-tid res (tid -1))]
      (is (= ["Q1"] (map :query/name (to-seq list))))))

  (testing "two cons cells (list with length 2)"
    (let [res (with [{:db/id (tid -2) :query/name "Q1"}
                     {:db/id (tid -3) :query/name "Q2"}
                     [:l.fn/cons (tid -1) (tid -2) (tid -4)]
                     [:l.fn/cons (tid -4) (tid -3) nil]])
          list (resolve-tid res (tid -1))]
      (is (= ["Q1" "Q2"] (map :query/name (to-seq list)))))))

(deftest append-test
  (testing "append to empty list"
    (let [res (with [{:db/id (tid -2) :query/name "Q1"}
                     [:l.fn/append (tid -1) nil (tid -2)]])
          list (resolve-tid res (tid -1))]
      (is (= ["Q1"] (map :query/name (to-seq list))))))

  (testing "append to non-empty list"
    (let [res (with [{:db/id (tid -2) :query/name "Q1"}
                     [:l.fn/cons (tid -1) (tid -2) nil]])
          list (resolve-tid res (tid -1))
          res (with (:db-after res)
                    [{:db/id (tid -2) :query/name "Q2"}
                     [:l.fn/append (tid -1) (:db/id list) (tid -2)]])
          list (resolve-tid res (tid -1))]
      (is (= ["Q1" "Q2"] (map :query/name (to-seq list)))))))

(deftest update-test
  (testing "update first element in single element list"
    (let [res (with [{:db/id (tid -2) :query/name "Q1"}
                     [:l.fn/cons (tid -1) (tid -2) nil]])
          list (resolve-tid res (tid -1))
          res (with (:db-after res)
                    [{:db/id (tid -2) :query/name "X"}
                     [:l.fn/update (tid -1) (:db/id list) 0 (tid -2)]])
          list (resolve-tid res (tid -1))]
      (is (= ["X"] (map :query/name (to-seq list))))))

  (testing "update first element in two element list"
    (let [res (with [{:db/id (tid -2) :query/name "Q1"}
                     {:db/id (tid -3) :query/name "Q2"}
                     [:l.fn/cons (tid -1) (tid -2) (tid -4)]
                     [:l.fn/cons (tid -4) (tid -3) nil]])
          list (resolve-tid res (tid -1))
          res (with (:db-after res)
                    [{:db/id (tid -2) :query/name "X"}
                     [:l.fn/update (tid -1) (:db/id list) 0 (tid -2)]])
          list (resolve-tid res (tid -1))]
      (is (= ["X" "Q2"] (map :query/name (to-seq list))))))

  (testing "update second element in two element list"
    (let [res (with [{:db/id (tid -2) :query/name "Q1"}
                     {:db/id (tid -3) :query/name "Q2"}
                     [:l.fn/cons (tid -1) (tid -2) (tid -4)]
                     [:l.fn/cons (tid -4) (tid -3) nil]])
          list (resolve-tid res (tid -1))
          res (with (:db-after res)
                    [{:db/id (tid -3) :query/name "X"}
                     [:l.fn/update (tid -1) (:db/id list) 1 (tid -3)]])
          list (resolve-tid res (tid -1))]
      (is (= ["Q1" "X"] (map :query/name (to-seq list)))))))

(deftest add-query-test
  (testing "add query"
    (let [res (with [[:version.fn/create-initial (tid -1) (tid -2)]
                     [:query.fn/create (tid -3)]
                     [:l.fn/cons (tid -2) (tid -3) nil]])
          version (resolve-tid res (tid -1))
          res (with (:db-after res)
                    [[:version.fn/add-query (tid -1) (:db/id version)]])
          version (resolve-tid res (tid -1))]
      (is (= 2 (count (to-seq (:version/queries version))))))))

(deftest add-query-cell-test
  (testing "add query cell to [0 0]"
    (let [res (with [[:version.fn/create-initial (tid -1) (tid -2)]
                     [:query.fn/create (tid -3)]
                     [:l.fn/cons (tid -2) (tid -3) nil]])
          version (resolve-tid res (tid -1))
          res (with (:db-after res)
                    [[:version.fn/add-query-cell (tid -1) (:db/id version)
                      0 0 {:type :form :id "T1"}]])
          version (resolve-tid res (tid -1))
          query (first (to-seq (:version/queries version)))
          col (first (to-seq (:query/cols query)))
          cell (first (to-seq (:query.col/cells col)))]
      (is (= "T1" (:query.cell.term/id cell)))))
  (testing "add query cell to [0 1]"
    (let [res (with [[:version.fn/create-initial (tid -1) (tid -2)]
                     [:query.fn/create (tid -3)]
                     [:l.fn/cons (tid -2) (tid -3) nil]])
          version (resolve-tid res (tid -1))
          res (with (:db-after res)
                    [[:version.fn/add-query-cell (tid -1) (:db/id version)
                      0 1 {:type :form :id "T1"}]])
          version (resolve-tid res (tid -1))
          query (first (to-seq (:version/queries version)))
          col (second (to-seq (:query/cols query)))
          cell (first (to-seq (:query.col/cells col)))]
      (is (= "T1" (:query.cell.term/id cell)))))
  (testing "add query cell to [0 2]"
    (let [res (with [[:version.fn/create-initial (tid -1) (tid -2)]
                     [:query.fn/create (tid -3)]
                     [:l.fn/cons (tid -2) (tid -3) nil]])
          version (resolve-tid res (tid -1))
          res (with (:db-after res)
                    [[:version.fn/add-query-cell (tid -1) (:db/id version)
                      0 2 {:type :form :id "T1"}]])
          version (resolve-tid res (tid -1))
          query (first (to-seq (:version/queries version)))
          col (nth (to-seq (:query/cols query)) 2)
          cell (first (to-seq (:query.col/cells col)))]
      (is (= "T1" (:query.cell.term/id cell)))))
  (testing "add three query cells to [0 0], [0 1] and [0 2] in row"
    (let [res (with [[:version.fn/create-initial (tid -1) (tid -2)]
                     [:query.fn/create (tid -3)]
                     [:l.fn/cons (tid -2) (tid -3) nil]])
          version (resolve-tid res (tid -1))
          res (with (:db-after res)
                    [[:version.fn/add-query-cell (tid -1) (:db/id version)
                      0 0 {:type :form :id "T1"}]])
          version (resolve-tid res (tid -1))
          res (with (:db-after res)
                    [[:version.fn/add-query-cell (tid -1) (:db/id version)
                      0 1 {:type :form :id "T2"}]])
          version (resolve-tid res (tid -1))
          res (with (:db-after res)
                    [[:version.fn/add-query-cell (tid -1) (:db/id version)
                      0 2 {:type :form :id "T3"}]])
          version (resolve-tid res (tid -1))
          query (first (to-seq (:version/queries version)))
          col-1 (nth (to-seq (:query/cols query)) 0)
          col-2 (nth (to-seq (:query/cols query)) 1)
          col-3 (nth (to-seq (:query/cols query)) 2)
          cell-1 (first (to-seq (:query.col/cells col-1)))
          cell-2 (first (to-seq (:query.col/cells col-2)))
          cell-3 (first (to-seq (:query.col/cells col-3)))]
      (is (= "T1" (:query.cell.term/id cell-1)))
      (is (= "T2" (:query.cell.term/id cell-2)))
      (is (= "T3" (:query.cell.term/id cell-3))))))

(deftest remove-query-cell-test
  (testing "remove only query cell"
      (let [res (with [[:version.fn/create-initial (tid -1) (tid -2)]
                       [:query.fn/create (tid -3)]
                       [:l.fn/cons (tid -2) (tid -3) nil]])
            version (resolve-tid res (tid -1))
            res (with (:db-after res)
                      [[:version.fn/add-query-cell (tid -1) (:db/id version)
                        0 0 {:type :form :id "T1"}]])
            version (resolve-tid res (tid -1))
            res (with (:db-after res)
                      [[:version.fn/remove-query-cell (tid -1) (:db/id version)
                        0 0 "T1"]])
            version (resolve-tid res (tid -1))
            query (first (to-seq (:version/queries version)))
            col (first (to-seq (:query/cols query)))]
        (is (empty? (to-seq (:query.col/cells col))))))
  (testing "remove head query cell"
      (let [res (with [[:version.fn/create-initial (tid -1) (tid -2)]
                       [:query.fn/create (tid -3)]
                       [:l.fn/cons (tid -2) (tid -3) nil]])
            version (resolve-tid res (tid -1))
            res (with (:db-after res)
                      [[:version.fn/add-query-cell (tid -1) (:db/id version)
                        0 0 {:type :form :id "T2"}]])
            version (resolve-tid res (tid -1))
            res (with (:db-after res)
                      [[:version.fn/add-query-cell (tid -1) (:db/id version)
                        0 0 {:type :form :id "T1"}]])
            version (resolve-tid res (tid -1))
            res (with (:db-after res)
                      [[:version.fn/remove-query-cell (tid -1) (:db/id version)
                        0 0 "T1"]])
            version (resolve-tid res (tid -1))
            query (first (to-seq (:version/queries version)))
            col (first (to-seq (:query/cols query)))]
        (is (= ["T2"] (map :query.cell.term/id (to-seq (:query.col/cells col)))))))
  (testing "remove tail query cell"
      (let [res (with [[:version.fn/create-initial (tid -1) (tid -2)]
                       [:query.fn/create (tid -3)]
                       [:l.fn/cons (tid -2) (tid -3) nil]])
            version (resolve-tid res (tid -1))
            res (with (:db-after res)
                      [[:version.fn/add-query-cell (tid -1) (:db/id version)
                        0 0 {:type :form :id "T2"}]])
            version (resolve-tid res (tid -1))
            res (with (:db-after res)
                      [[:version.fn/add-query-cell (tid -1) (:db/id version)
                        0 0 {:type :form :id "T1"}]])
            version (resolve-tid res (tid -1))
            res (with (:db-after res)
                      [[:version.fn/remove-query-cell (tid -1) (:db/id version)
                        0 0 "T2"]])
            version (resolve-tid res (tid -1))
            query (first (to-seq (:version/queries version)))
            col (first (to-seq (:query/cols query)))]
        (is (= ["T1"] (map :query.cell.term/id (to-seq (:query.col/cells col)))))))
  (testing "remove middle query cell from three"
      (let [res (with [[:version.fn/create-initial (tid -1) (tid -2)]
                       [:query.fn/create (tid -3)]
                       [:l.fn/cons (tid -2) (tid -3) nil]])
            version (resolve-tid res (tid -1))
            res (with (:db-after res)
                      [[:version.fn/add-query-cell (tid -1) (:db/id version)
                        0 0 {:type :form :id "T3"}]])
            version (resolve-tid res (tid -1))
            res (with (:db-after res)
                      [[:version.fn/add-query-cell (tid -1) (:db/id version)
                        0 0 {:type :form :id "T2"}]])
            version (resolve-tid res (tid -1))
            res (with (:db-after res)
                      [[:version.fn/add-query-cell (tid -1) (:db/id version)
                        0 0 {:type :form :id "T1"}]])
            version (resolve-tid res (tid -1))
            res (with (:db-after res)
                      [[:version.fn/remove-query-cell (tid -1) (:db/id version)
                        0 0 "T2"]])
            version (resolve-tid res (tid -1))
            query (first (to-seq (:version/queries version)))
            col (first (to-seq (:query/cols query)))]
        (is (= ["T1" "T3"] (map :query.cell.term/id (to-seq (:query.col/cells col))))))))

(deftest remove-query-test
  (testing "remove only query"
      (let [res (with [[:version.fn/create-initial (tid -1) (tid -2)]
                       [:query.fn/create (tid -3)]
                       [:l.fn/cons (tid -2) (tid -3) nil]])
            version (resolve-tid res (tid -1))
            res (with (:db-after res)
                      [[:version.fn/remove-query (tid -1) (:db/id version) 0]])
            version (resolve-tid res (tid -1))]
        (is (empty? (to-seq (:version/queries version))))))
  (testing "remove first query out of two queries"
      (let [res (with [[:version.fn/create-initial (tid -1) (tid -2)]
                       [:query.fn/create (tid -3)]
                       [:l.fn/cons (tid -2) (tid -3) (tid -4)]
                       [:query.fn/create (tid -5)]
                       [:l.fn/cons (tid -4) (tid -5) nil]])
            version (resolve-tid res (tid -1))
            q2 (resolve-tid res (tid -5))
            res (with (:db-after res)
                      [[:version.fn/remove-query (tid -1) (:db/id version) 0]])
            version (resolve-tid res (tid -1))]
        (is (= [q2] (to-seq (:version/queries version))))))
  (testing "remove second query out of two queries"
      (let [res (with [[:version.fn/create-initial (tid -1) (tid -2)]
                       [:query.fn/create (tid -3)]
                       [:l.fn/cons (tid -2) (tid -3) (tid -4)]
                       [:query.fn/create (tid -5)]
                       [:l.fn/cons (tid -4) (tid -5) nil]])
            version (resolve-tid res (tid -1))
            q1 (resolve-tid res (tid -3))
            res (with (:db-after res)
                      [[:version.fn/remove-query (tid -1) (:db/id version) 1]])
            version (resolve-tid res (tid -1))]
        (is (= [q1] (to-seq (:version/queries version)))))))
