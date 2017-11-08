(ns dante.middleware
  (:require [compojure.core :refer :all]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [prone.middleware :refer [wrap-exceptions]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.json :refer [wrap-json-params wrap-json-response]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [clojure.string :as string]
            [dante.util :refer [frame-text info]]))

(defn date [& {:keys [time format]
               :or   {time (new java.util.Date)
                      format "MM/dd/yyyy - hh:mm:ss"}}]
  (str (.format (java.text.SimpleDateFormat. format) time)))

(defn prn-map
  ([prefix val]
   (if (and (map? val) (not (empty? val)))
     (let [ks (keys val)
           res (map (fn [k] (prn-map (conj prefix k) (get val k))) ks)]
       (string/join "\n" res))
     (let [names (map name prefix)
           namespace (str (keyword (string/join "/" names)))
           val   (if (= {} val) "{}" (if (string/blank? (str val)) "nil" (str val)))
           label (str namespace)]
       (format "%-40s - %s" label val))))
  ([hashmap]
   (map #(prn-map (vector %) (get hashmap %)) (keys hashmap))))

(defn wrap-user [handler]
  (fn [req]
    (let [vec (str (first (:compojure/route req)))
          uri (:uri req)]
      (println "\n")
      (frame-text (date))
      (info (str vec " - " uri))
      (handler req))))

(defn wrap-middleware [handler]
  (-> handler
      (wrap-routes wrap-keyword-params)
      (wrap-routes wrap-json-params)
      (wrap-routes wrap-json-response)
      (wrap-routes wrap-user)
      (wrap-routes wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))
      (wrap-routes wrap-params)
      wrap-exceptions
      wrap-reload))
