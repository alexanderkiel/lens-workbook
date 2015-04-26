(ns lens.route
  (:require [bidi.bidi :as bidi]))

;; ---- Routes ----------------------------------------------------------------

(def routes
  ["/" {"" :service-document-handler
        "workbooks" :create-workbook-handler
        "private-workbooks" :private-workbook-list
        ["workbooks/" :id] :workbook-handler
        ["workbooks/" :id "/create-branch"] :create-branch-handler
        ["workbooks/" :id "/add-query"] :add-query-handler
        "branches" :branch-list-handler
        ["branches/" :id] {:get :get-branch-handler
                           :put :put-branch-handler}}])

(defn path-for [handler & params]
  (apply bidi/path-for routes handler params))
