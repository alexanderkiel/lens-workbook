(ns lens.route)

(defn routes [context-path]
  [(if (= "/" context-path) "" context-path)
   {(if (= "/" context-path) "/" "") :service-document-handler
    "/find-workbook" :find-workbook-handler
    "/private-workbooks" :all-private-workbooks
    ["/workbooks/" :id] {:get :get-workbook-handler
                         :put :put-workbook-handler}
    ["/versions/" :id]
    {"" :version-handler
     "/add-query" :add-query-handler
     "/remove-query" :remove-query-handler
     "/duplicate-query" :duplicate-query-handler
     "/add-query-cell" :add-query-cell-handler
     "/remove-query-cell" :remove-query-cell-handler}}])
