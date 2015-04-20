(ns lens.api
  (:require [datomic.api :as d]))

(defn find-workbook [db id]
  (d/entity db [:workbook/id id]))
