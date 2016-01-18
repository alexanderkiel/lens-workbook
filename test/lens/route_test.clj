(ns lens.route-test
  (:require [clojure.test :refer :all]
            [lens.route :refer :all]
            [bidi.bidi :as bidi]
            [schema.test :refer [validate-schemas]]))

(use-fixtures :once validate-schemas)

(deftest routes-test
  (testing "Root Context Path is ok"
    (is (routes "")))

  (testing "Context Path starting and not ending with a slash is ok"
    (is (routes "/a")))

  (testing "Context Path with two path segments is ok"
    (is (routes "/a/b")))

  (testing "Context Path ending with a slash is invalid"
    (is (thrown? Exception (routes "/")))
    (is (thrown? Exception (routes "/a/"))))

  (testing "Double slash Context Path is invalid"
    (is (thrown? Exception (routes "//"))))

  (testing "With root context path"
    (are [handler params path] (= path (apply bidi/path-for (routes "")
                                              handler params))
      :service-document-handler [] "/"
      :find-workbook-handler [] "/find-workbook"
      :all-private-workbooks [] "/private-workbooks"
      :workbook-handler [:id "110915"] "/workbooks/110915"
      :version-handler [:id "110912"] "/versions/110912"
      :add-query-handler [:id "111001"] "/versions/111001/add-query"))

  (testing "With context path /111316"
    (are [handler params path] (= path (apply bidi/path-for (routes "/111316")
                                              handler params))
      :service-document-handler [] "/111316"
      :find-workbook-handler [] "/111316/find-workbook"
      :all-private-workbooks [] "/111316/private-workbooks"
      :workbook-handler [:id "110915"] "/111316/workbooks/110915"
      :version-handler [:id "110912"] "/111316/versions/110912"
      :add-query-handler [:id "111001"] "/111316/versions/111001/add-query")))
