(ns lens.util
  (:require [schema.core :as s])
  (:import [datomic Entity]
           [java.util UUID]))

(defn parse-long [s]
  (Long/parseLong s))

(defn uuid? [x]
  (instance? UUID x))

;; ---- Datomic ---------------------------------------------------------------

(defn entity?
  "Test if x is a Datomic entity."
  [x]
  (instance? Entity x))

(defn to-seq
  "Converts a linked list into a lazy seq."
  [linked-list]
  (when linked-list
    (cons (:l/head linked-list)
          (lazy-seq (to-seq (:l/tail linked-list))))))

;; ---- Schema ----------------------------------------------------------------

(def Nat
  (s/constrained s/Int (comp not neg?) 'not-neg))
