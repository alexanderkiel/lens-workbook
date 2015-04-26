(ns lens.middleware.cors)

(defn assoc-header [response]
  (assoc-in response [:headers "Access-Control-Allow-Origin"] "*"))

(defn wrap-cors
  "Adds an Access-Control-Allow-Origin header with the value * to responses."
  [handler]
  (fn [request]
    (if (= :options (:request-method request))
      {:status 204
       :headers {"Access-Control-Allow-Origin" "*"
                 "Access-Control-Allow-Methods" "GET"
                 "Access-Control-Allow-Headers" "Authorization, Accept"}}
      (-> (handler request)
          (assoc-header)))))
