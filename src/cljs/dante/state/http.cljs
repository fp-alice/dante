(ns dante.state.http
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [dante.state.storage :as storage]
            [dante.util :refer [url]]
            [re-frame.core :as re-frame])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- json-req [location params-map function & type]
  "Sends request to `location` with `params-map` and runs `function` on the result. Optional MIME type."
  (go (let [url      (str url location)
            response (<! (http/post
                          url
                          {:headers     {"content-type" (or type "application/json")}
                           :json-params params-map}))
            body     (:body response)]
        (if-not (= nil body)
          (function body)))))

(defn get-session [user pass]
  (json-req "api/user-session" {:username user
                                :password pass}
            #(do
               ;; (println %)
               (if-not (nil? (:session %))
                 (do
                   (storage/set-item! "session" (:session %))
                   (re-frame/dispatch [:set-session (:session %)])
                   (re-frame/dispatch [:set-key (:key %)])
                   (re-frame/dispatch [:set-images (:images %)])
                   (re-frame/dispatch [:set-username (:username %)])
                   (re-frame/dispatch [:set-panel :home]))))))

(defn auth-with-session [session]
  ;; (println session)
  (json-req "api/auth" {:session session}
            #(do
               (let [user (:user %)]
                 ;; (println user)
                 (if-not (nil? (:session user))
                   (do
                     (storage/set-item! "session" (:session user))
                     (re-frame/dispatch [:set-session (:session user)])
                     (re-frame/dispatch [:set-key (:key user)])
                     (re-frame/dispatch [:set-images (:images user)])
                     (re-frame/dispatch-sync [:set-username (:username user)])
                     (re-frame/dispatch-sync [:set-panel :home])))))))

(defn get-script [key url]
  ;; (println session)
  (json-req "api/script" {:key key
                          :url url}
            #(re-frame/dispatch [:set-script %])))

(defn sign-up [username password]
  ;; (println session)
  (json-req "api/sign-up" {:username username
                           :password password}
            #(do
               (let [user (:user %)]
                 ;; (println user)
                 (if-not (nil? (:session user))
                   (do
                     (storage/set-item! "session" (:session user))
                     (re-frame/dispatch [:set-session (:session user)])
                     (re-frame/dispatch [:set-key (:key user)])
                     (re-frame/dispatch [:set-images (:images user)])
                     (re-frame/dispatch [:set-username (:username user)])
                     (re-frame/dispatch [:set-panel :home])))))))

(defn auth-if-session! []
  "Authenticate user if stored session exists"
  (let [key? (nil? (storage/get-item "session"))]
    (if-not key?
      (auth-with-session (storage/get-item "session")))))

(defn delete-img [key md5]
  (json-req "api/delete" {:key key
                          :md5 md5}
            #(do (auth-if-session!))))
