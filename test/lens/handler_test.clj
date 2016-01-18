(ns lens.handler-test
  (:require [clojure.test :refer :all]
            [clojure.data.json :as json]
            [lens.handler :refer :all]
            [lens.test-util :refer :all]
            [schema.test :refer [validate-schemas]]
            [juxt.iota :refer [given]]
            [schema.core :as s]
            [lens.api :as api]
            [datomic.api :as d]
            [org.httpkit.fake :refer [with-fake-http]]))

(use-fixtures :each database-fixture)
(use-fixtures :once validate-schemas)

(deftest service-document-handler-test
  (let [resp (execute (service-document-handler "version-142728") :get)]

    (is (= 200 (:status resp)))

    (testing "Response contains an ETag"
      (is (get-in resp [:headers "ETag"])))

    (testing "Data contains name and version"
      (given (-> resp :body :data)
        :name := "Lens Workbook"
        :version := "version-142728"))

    (testing "Body contains a self link"
      (given (self-href resp)
        :handler := :service-document-handler))

    (testing "Body contains a private workbooks link"
      (given (-> resp :body :links :lens/private-workbooks href)
        :handler := :all-private-workbooks))

    (testing "Body contains a find workbook query"
      (given (-> resp :body :queries :lens/find-workbook)
        [href :handler] := :find-workbook-handler
        [:params :id :type] := s/Str))))

(defn find-workbook [id]
  (api/workbook (d/db (connect)) id))

(defn- workbook-etag [id]
  (-> (execute workbook-handler :get :params {:id id})
      (get-in [:headers "ETag"])))

(defn add-query [version]
  (api/add-query (connect) version))

(deftest workbook-handler-test
  (let [id (:workbook/id (create-workbook "name-171726"))
        resp (execute workbook-handler :get
               :params {:id id})]

    (is (= 200 (:status resp)))

    (testing "Body contains an up link"
      (given (up-href resp)
        :handler := :service-document-handler))

    (testing "Body contains a self link"
      (given (self-href resp)
        :handler := :workbook-handler
        :args := [:id id]))

    (testing "Body contains a head link"
      (given (-> resp :body :links :lens/head href)
        :handler := :version-handler))

    (testing "Body contains a profile link"
      (given (-> resp :body :links :profile href)
        :handler := :workbook-profile-handler))

    (testing "Response contains an ETag"
      (is (get-in resp [:headers "ETag"])))

    (testing "Caching is disabled"
      (is (= "no-cache" (get-in resp [:headers "cache-control"]))))

    (testing "Data contains the workbook id and name"
      (given (-> resp :body :data)
        :id := id
        :name := "name-171726"))

    (testing "Operations contain :update"
      (is (contains? (-> resp :body :ops) :update))))

  (testing "Non-conditional update fails"
    (given (execute workbook-handler :put)
      :status := 428
      error-msg := "Require conditional update."))

  (testing "Update fails on missing request body"
    (given (execute workbook-handler :put
             [:headers "if-match"] "\"foo\"")
      :status := 400
      error-msg := "Missing request body."))

  (testing "Update fails on missing version-id"
    (let [id (:workbook/id (create-workbook "name-100007"))]
      (given (execute workbook-handler :put
               :params {:id id}
               :body {:data {}}
               [:headers "if-match"] "\"foo\"")
        :status := 422
        error-msg :# "Unprocessable Entity.+"
        error-msg :# ".+head-id.+")))

  (testing "Update fails on ETag missmatch"
    (let [id (:workbook/id (create-workbook "name-101028"))]
      (given (execute workbook-handler :put
               :params {:id id}
               :body {:data
                      {:head-id "version-id-101705"}}
               [:headers "if-match"] "\"foo\"")
        :status := 412)))

  (testing "Update succeeds"
    (let [workbook (create-workbook "name-101816")
          id (:workbook/id workbook)
          version-id (:version/id (add-query (:workbook/head workbook)))]
      (given (execute workbook-handler :put
               :params {:id id}
               :body {:data
                      {:id id
                       :name name
                       :head-id version-id}}
               [:headers "if-match"] (workbook-etag id))
        :status := 204)
      (is (= version-id (-> (find-workbook id) :workbook/head :version/id))))))

(defn- auth-req [token-uri username]
  [token-uri
   {:status 200 :body (json/write-str {:active true :username username})}])

(deftest all-private-workbooks-handler-test
  (let [resp (->> (execute (all-private-workbooks-handler "uri-191624") :get
                    :headers {"authorization" "Bearer token-193326"})
                  (with-fake-http (auth-req "uri-191624" "name-193112")))]

    (is (= 200 (:status resp)))

    (testing "Body contains an up link"
      (given (up-href resp)
        :handler := :service-document-handler))

    (testing "Body contains a self link"
      (given (self-href resp)
        :handler := :all-private-workbooks)))

  (testing "Request without Authorization header fails"
    (given (execute (all-private-workbooks-handler "/token-191624") :get)
      :status := 401
      error-msg := "Missing Authorization header."))

  (testing "Request with unsupported Authorization scheme fails"
    (given (execute (all-private-workbooks-handler "/token-191624") :get
             :headers {"authorization" ""})
      :status := 401
      error-msg := "Unsupported authentication scheme. Expect Bearer."))

  (testing "Create succeeds"
    (given (->> (execute (all-private-workbooks-handler "uri-131028") :post
                  :headers {"authorization" "Bearer token-193326"}
                  :params {:name "name-131049"})
                (with-fake-http (auth-req "uri-131028" "name-131044")))
      :status := 201
      :body := nil
      [location :handler] := :workbook-handler
      [location :args] :> [:id])))

(deftest find-workbook-handler-test
  (let [id (:workbook/id (create-workbook))]
    (given (execute find-workbook-handler :get
             :params {:id id})
      :status := 301
      :body := nil
      [location :handler] := :workbook-handler
      [location :args] :> [:id])))
