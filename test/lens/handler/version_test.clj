(ns lens.handler.version-test
  (:require [clojure.test :refer :all]
            [clojure.data.json :as json]
            [lens.handler.version :refer :all]
            [lens.test-util :refer :all]
            [schema.test :refer [validate-schemas]]
            [juxt.iota :refer [given]]
            [schema.core :refer [Int Str]]
            [lens.api :as api]
            [datomic.api :as d]
            [lens.util :as util]))

(use-fixtures :each database-fixture)
(use-fixtures :once validate-schemas)

(deftest handler-test
  (let [id (:version/id (:workbook/head (create-workbook)))
        resp (execute handler :get
               :params {:id id})]

    (is (= 200 (:status resp)))

    (testing "Body contains an up link"
      (given (up-href resp)
        :handler := :service-document-handler))

    (testing "Body contains a self link"
      (given (self-href resp)
        :handler := :version-handler
        :args := [:id id]))

    (testing "Body contains no parent link"
      (is (nil? (-> resp :body :links :lens/parent))))

    (testing "Response contains an ETag"
      (is (get-in resp [:headers "ETag"])))

    (testing "Responses are cacheable"
      (is (= "max-age=86400" (get-in resp [:headers "cache-control"]))))

    (testing "Data contains the version id and its queries"
      (given (-> resp :body :data)
        :id := id
        [:queries count] := 1))

    (testing "Response contains an add query form"
      (given (-> resp :body :forms :lens/add-query)
        [href :handler] := :add-query-handler))

    (testing "Response contains a remove query form"
      (given (-> resp :body :forms :lens/remove-query)
        [href :handler] := :remove-query-handler
        [:params :idx :type] := Int))

    (testing "Response contains a duplicate query form"
      (given (-> resp :body :forms :lens/duplicate-query)
        [href :handler] := :duplicate-query-handler
        [:params :idx :type] := Int))

    (testing "Response contains an add query cell form"
      (given (-> resp :body :forms :lens/add-query-cell)
        [href :handler] := :add-query-cell-handler
        [:params :query-idx :type] := Int
        [:params :col-idx :type] := Int
        [:params :term-type :type] := (:type api/Term)
        [:params :term-id :type] := Str))

    (testing "Response contains a remove query cell form"
      (given (-> resp :body :forms :lens/remove-query-cell)
        [href :handler] := :remove-query-cell-handler
        [:params :query-idx :type] := Int
        [:params :col-idx :type] := Int))))

(defn version [id]
  (api/version (d/db (connect)) id))

(deftest add-query-handler-test
  (let [id (:version/id (:workbook/head (create-workbook)))]

    (testing "Create succeeds"
      (let [resp (execute add-query-handler :post
                   :params {:id id})]

        (testing "Response"
          (given resp
            :status := 201
            :body := nil
            [location :handler] := :version-handler
            [location :args] :> [:id]
            [location :args] :!> [id]))

        (testing "New Version has two queries"
          (let [new-version (-> resp location :args second version)]
            (is (= 2 (count (:version/queries new-version))))))))))

(deftest remove-query-handler-test
  (testing "Workbook with one query"
    (let [id (:version/id (:workbook/head (create-workbook)))]

      (testing "Index out of bounds."
        (given (execute remove-query-handler :post
                 :params {:id id :idx 1})
          :status := 409
          error-msg := "Index 1 out of bounds. Number of queries is 1."))

      (testing "Create succeeds"
        (let [resp (execute remove-query-handler :post
                     :params {:id id :idx 0})]

          (testing "Response"
            (given resp
              :status := 201
              :body := nil
              [location :handler] := :version-handler
              [location :args] :> [:id]
              [location :args] :!> [id]))

          (testing "New Version has no queries"
            (let [new-version (-> resp location :args second version)]
              (is (empty? (util/to-seq (:version/queries new-version))))))))))

  (testing "Workbook with two queries"
    (let [v (:workbook/head (create-workbook))
          v (api/add-query (connect) v)
          id (:version/id v)]

      (testing "Index out of bounds."
        (given (execute remove-query-handler :post
                 :params {:id id :idx 2})
          :status := 409
          error-msg := "Index 2 out of bounds. Number of queries is 2."))

      (testing "Removing the second query succeeds"
        (let [resp (execute remove-query-handler :post
                     :params {:id id :idx 1})]

          (testing "Response"
            (is (= 201 (:status resp))))

          (testing "New Version has one query"
            (let [new-version (-> resp location :args second version)]
              (is (= 1 (count (util/to-seq (:version/queries new-version)))))))))

      (testing "Removing the first query succeeds"
        (let [resp (execute remove-query-handler :post
                     :params {:id id :idx 0})]

          (testing "Response"
            (is (= 201 (:status resp))))

          (testing "New Version has one query"
            (let [new-version (-> resp location :args second version)]
              (is (= 1 (count (util/to-seq (:version/queries new-version)))))))))))

  (testing "Workbook with three queries"
    (let [v (:workbook/head (create-workbook))
          v (api/add-query (connect) v)
          v (api/add-query (connect) v)
          id (:version/id v)]

      (testing "Index out of bounds."
        (given (execute remove-query-handler :post
                 :params {:id id :idx 3})
          :status := 409
          error-msg := "Index 3 out of bounds. Number of queries is 3."))

      (testing "Removing the third query succeeds"
        (let [resp (execute remove-query-handler :post
                     :params {:id id :idx 2})]

          (testing "Response"
            (is (= 201 (:status resp))))

          (testing "New Version has two queries"
            (let [new-version (-> resp location :args second version)]
              (is (= 2 (count (util/to-seq (:version/queries new-version)))))))))

      (testing "Removing the second query succeeds"
        (let [resp (execute remove-query-handler :post
                     :params {:id id :idx 1})]

          (testing "Response"
            (is (= 201 (:status resp))))

          (testing "New Version has two queries"
            (let [new-version (-> resp location :args second version)]
              (is (= 2 (count (util/to-seq (:version/queries new-version)))))))))

      (testing "Removing the first query succeeds"
        (let [resp (execute remove-query-handler :post
                     :params {:id id :idx 0})]

          (testing "Response"
            (is (= 201 (:status resp))))

          (testing "New Version has two queries"
            (let [new-version (-> resp location :args second version)]
              (is (= 2 (count (util/to-seq (:version/queries new-version))))))))))))

(deftest duplicate-query-handler-test
  (let [id (:version/id (:workbook/head (create-workbook)))]

    (testing "Index out of bounds."
      (given (execute duplicate-query-handler :post
               :params {:id id :idx 1})
        :status := 409
        error-msg := "Index 1 out of bounds. Number of queries is 1."))

    (testing "Create succeeds"
      (let [resp (execute duplicate-query-handler :post
                   :params {:id id :idx 0})]

        (testing "Response"
          (given resp
            :status := 201
            :body := nil
            [location :handler] := :version-handler
            [location :args] :> [:id]
            [location :args] :!> [id]))

        (testing "New Version has two queries"
          (let [new-version (-> resp location :args second version)]
            (is (= 2 (count (:version/queries new-version))))))))))

(deftest add-query-cell-handler-test
  (let [id (:version/id (:workbook/head (create-workbook)))]

    (testing "Query index out of bounds."
      (given (execute add-query-cell-handler :post
               :params {:id id :query-idx 1 :col-idx 0
                        :term-type :form :term-id ""})
        :status := 409
        error-msg := "Index 1 out of bounds. Number of queries is 1."))

    (testing "Column index out of bounds."
      (given (execute add-query-cell-handler :post
               :params {:id id :query-idx 0 :col-idx 3
                        :term-type :form :term-id ""})
        :status := 409
        error-msg := "Index 3 out of bounds. Number of columns is 3."))

    (testing "Create succeeds"
      (let [resp (execute add-query-cell-handler :post
                   :params {:id id :query-idx 0 :col-idx 0
                            :term-type :form :term-id "term-id-181051"})]

        (testing "Response"
          (given resp
            :status := 201
            :body := nil
            [location :handler] := :version-handler
            [location :args] :> [:id]
            [location :args] :!> [id]))

        (testing "New Version has a query cell"
          (let [new-version (-> resp location :args second version)
                query (-> new-version api/queries first)
                col (-> query api/query-cols first)
                cell (-> col api/query-cells first)]
            (given cell
              :query.cell.term/type := :form
              :query.cell.term/id := "term-id-181051")))))))

(deftest remove-query-cell-handler-test
  (let [id (:version/id (:workbook/head (create-workbook)))]

    (testing "Query index out of bounds."
      (given (execute remove-query-cell-handler :post
               :params {:id id :query-idx 1 :col-idx 0 :term-id ""})
        :status := 409
        error-msg := "Index 1 out of bounds. Number of queries is 1."))

    (testing "Column index out of bounds."
      (given (execute remove-query-cell-handler :post
               :params {:id id :query-idx 0 :col-idx 3 :term-id ""})
        :status := 409
        error-msg := "Index 3 out of bounds. Number of columns is 3."))

    (testing "Create succeeds"
      (let [resp (execute remove-query-cell-handler :post
                   :params {:id id :query-idx 0 :col-idx 0
                            :term-id "term-id-183114"})]

        (testing "Response"
          (given resp
            :status := 201
            :body := nil
            [location :handler] := :version-handler
            [location :args] :> [:id]
            [location :args] :!> [id]))

        (testing "New Version has no query cell"
          (let [new-version (-> resp location :args second version)
                query (-> new-version api/queries first)
                col (-> query api/query-cols first)]
            (is (-> col api/query-cells empty?))))))))
