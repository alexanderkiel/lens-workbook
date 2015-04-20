(ns lens.routes
  (:use plumbing.core)
  (:require [compojure.core :as compojure :refer [GET POST DELETE]]
            [ring.util.response :as ring-resp]
            [lens.api :as api]
            [clojure.core.async :refer [timeout]]))

;; ---- Service Document ------------------------------------------------------

(defn find-workbook-form []
  {:action "/find-workbook"
   :method "GET"
   :title "Find Workbook"
   :params
   {:id
    {:type :string
     :description "The :id of the workbook."}}})

(defn service-document [version]
  (ring-resp/response
   {:name "Lens Workbook"
    :version version
    :forms
    {:lens/find-workbook (find-workbook-form)}}))

;; ---- Find Workbook ---------------------------------------------------------

(defn find-workbook [db id]
  (if (api/find-workbook db id)
    (ring-resp/response
      {:links
       {:lens/workbook (str "/workbook/" id)
        :up {:href ""}}
       :forms
       {:lens/find-workbook (find-workbook-form)}})
    (ring-resp/response
      {:links
       {:up {:href ""}}
       :forms
       {:lens/find-workbook (find-workbook-form)}})))

;; ---- Routes ----------------------------------------------------------------

(defn routes [version]
  (compojure/routes
   (GET "/" [db] (service-document version))

   (GET "/find-workbook" [db id] (find-workbook db id))

   (fn [_] (ring-resp/not-found
             {:links {:up {:href ""}}
              :error "Resource not found."}))))
