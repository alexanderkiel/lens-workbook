(ns lens.util
  (:import [datomic Entity]
           [java.util UUID]))

(defn parse-int [s]
  (Integer/parseInt s))

(defn uuid? [x]
  (instance? UUID x))

;; ---- Datomic ---------------------------------------------------------------

(defn entity?
  "Test if x is a Datomic entity."
  [x]
  (instance? Entity x))
