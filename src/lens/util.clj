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

(defn to-seq
  "Converts a linked list into a lazy seq."
  [linked-list]
  {:pre [(:linked-list/head linked-list)]}
  (cons (:linked-list/head linked-list)
        (lazy-seq (when-let [tail (:linked-list/tail linked-list)]
                    (to-seq tail)))))
