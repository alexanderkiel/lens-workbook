(ns lens.schema
  (:require [datomic.api :as d]))

(def base-schema
  {:attributes
   [{:db/ident :workbook/id
     :db/valueType :db.type/string
     :db/unique :db.unique/identity
     :db/cardinality :db.cardinality/one
     :db/doc "The identifier of a workbook."}]})

(defn- assoc-db-id [m]
  (assoc m :db/id (d/tempid :db.part/db)))

(defn make-attr
  "Assocs :db/id and :db.install/_attribute to the attr map."
  [attr]
  (-> (assoc-db-id attr)
      (assoc :db.install/_attribute :db.part/db)))

(defn prepare-schema [schema]
  (mapv make-attr (:attributes schema)))

(defn load-base-schema
  "Loads the base schema in one transaction and derefs the result."
  [conn]
  (->> (prepare-schema base-schema)
       (d/transact conn)
       (deref)))
