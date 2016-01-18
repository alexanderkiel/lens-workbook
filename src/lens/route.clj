(ns lens.route
  (:require [schema.core :as s]))

(def ContextPath
  #"^(?:/[^/]+)*$")

(s/defn routes [context-path :- ContextPath]
  [context-path
   [[(if (= "" context-path) "/" "") :service-document-handler]
    ["/find-workbook" :find-workbook-handler]
    ["/private-workbooks" :all-private-workbooks]
    [["/workbooks/" :id] :workbook-handler]
    [["/versions/" :id]
     {"" :version-handler
      "/add-query" :add-query-handler
      "/remove-query" :remove-query-handler
      "/duplicate-query" :duplicate-query-handler
      "/add-query-cell" :add-query-cell-handler
      "/remove-query-cell" :remove-query-cell-handler}]
    ["/profiles"
     {"/workbook" :workbook-profile-handler}]]])
