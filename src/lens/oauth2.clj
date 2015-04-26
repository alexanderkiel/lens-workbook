(ns lens.oauth2
  (:require [clojure.core.async :as async]
            [clojure.data.json :as json]
            [org.httpkit.client :refer [post]]))

(defn- decode-resp [resp]
  (if (= 200 (:status resp))
    (let [user-info (json/read-str (:body resp) :key-fn keyword)]
      (if (:active user-info)
        {:user-info
         (-> (dissoc user-info :active)
             (assoc :sub (:username user-info)))}
        {}))
    {}))

(defn introspect
  "Introspects the token. Returns a channel conveying a map with :user-info or
  an empty map if the token is not active or there was another problem.

  The user-info map has the keys :username."
  [token-introspection-uri token]
  (let [ch (async/chan 1 (map decode-resp))]
    (post token-introspection-uri
          {:headers {"accept" "application/json"}
           :form-params {:token token}}
          #(async/put! ch %))
    ch))
