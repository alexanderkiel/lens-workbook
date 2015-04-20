(ns lens.routes
  (:use plumbing.core)
  (:require [compojure.core :as compojure :refer [GET POST DELETE]]
            [ring.util.response :as ring-resp]
            [lens.api :as api]
            [clojure.core.async :refer [timeout]]
            [datomic.api :as d])
  (:import [java.util UUID]))

;; ---- Service Document ------------------------------------------------------

(defn find-workbook-form []
  {:action "/find-workbook"
   :method "GET"
   :title "Find Workbook"
   :params
   {:id
    {:type :string
     :description "The :id of the workbook."}}})

(defn create-workbook-form []
  {:action "/workbooks"
   :method "POST"
   :title "Create New Workbook"})

(defn service-document [version]
  (ring-resp/response
   {:name "Lens Workbook"
    :version version
    :links
    {:self {:href "/"}}
    :forms
    {:lens/find-workbook (find-workbook-form)
     :lens/create-workbook (create-workbook-form)}}))

;; ---- Workbook --------------------------------------------------------------

(defn workbook [db id]
  (if-let [workbook (api/workbook db (UUID/fromString id))]
    (ring-resp/response
      {:links
       {:up {:href "/workbooks"}
        :self {:href (str "/workbooks/" id)}
        :lens/queries
        (->> (api/queries workbook)
             (mapv #(hash-map :href (str "/queries/" (:query/id %)))))}})
    (ring-resp/not-found
      {:links {:up {:href "/"}}
       :error "Workbook not found."})))

;; ---- Find Workbook ---------------------------------------------------------

(defn find-workbook [db id]
  (if (api/workbook db (UUID/fromString id))
    (ring-resp/response
      {:links
       {:up {:href "/"}
        :lens/workbook {:href (str "/workbooks/" id)}}
       :forms
       {:lens/find-workbook (find-workbook-form)}})
    (ring-resp/response
      {:links
       {:up {:href "/"}}
       :forms
       {:lens/find-workbook (find-workbook-form)}})))

;; ---- Create Workbook -------------------------------------------------------

(defn create-workbook [conn]
  (let [workbook (api/add-standard-workbook conn)]
    (ring-resp/created
      (str "/workbooks/" (:workbook/id workbook))
      {:links
       {:up {:href "/"}}})))

;; ---- Query -----------------------------------------------------------------

(defn render-embedded-query-cell [cell]
  {:links
   {:self {:href (str "/query-cells/" (:query-cell/id cell))}}
   :type (:query-cell.term/type cell)
   :id (:query-cell.term/id cell)})

(defn render-embedded-query-col [col]
  {:links
   {:self {:href (str "/query-cols/" (:query-col/id col))}}
   :embedded
   {:lens/query-cells
    (->> (api/query-cells col)
         (mapv #(render-embedded-query-cell %)))}})

(defn query [db id]
  (if-let [query (api/query db (UUID/fromString id))]
    (ring-resp/response
      {:links
       {:up {:href (str "/workbooks/" (-> query :query/workbook :workbook/id))}
        :self {:href (str "/queries/" (:query/id query))}}
       :embedded
       {:lens/query-cols
        (->> (api/query-cols query)
             (mapv #(render-embedded-query-col %)))}})
    (ring-resp/not-found
      {:links {:up {:href "/"}}
       :error "Query not found."})))

;; ---- Routes ----------------------------------------------------------------

(def uuid #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

(defn routes [version]
  (compojure/routes
   (GET "/" [db] (service-document version))

   (GET ["/workbooks/:id" :id uuid] [db id] (workbook db id))

   (GET "/find-workbook" [db id] (find-workbook db id))

   (POST "/workbooks" [conn] (create-workbook conn))
   
   (GET ["/queries/:id" :id uuid] [db id] (query db id))

   (fn [_] (ring-resp/not-found
             {:links {:up {:href "/"}}
              :error "Resource not found."}))))
