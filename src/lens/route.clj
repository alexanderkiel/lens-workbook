(ns lens.route
  (:require [bidi.bidi :as bidi]))

;; ---- Routes ----------------------------------------------------------------

(def routes
  ["/" {"" :service-document-handler
        "find-workbook" :find-workbook-handler
        "private-workbooks" :private-workbook-list
        ["workbooks/" :id] {:get :get-workbook-handler
                            :put :put-workbook-handler}
        ["versions/" :id] :version-handler
        ["versions/" :id "/add-query"] :add-query-handler
        ["versions/" :id "/add-query-cell"] :add-query-cell-handler
        ["versions/" :id "/remove-query-cell"] :remove-query-cell-handler}])

(defn path-for [handler & params]
  (apply bidi/path-for routes handler params))
