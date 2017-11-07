(ns dante.state.http
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [dante.state.storage :as storage]
            [re-frame.core :as re-frame]
            [dante.state.db :as db])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def url "http://localhost:3000/")

(defn- json-req [location params-map function & type]
  (go (let [url (str url location)
            response (<! (http/post
                          url
                          {:headers           {"content-type" (or type "application/json")}
                           :json-params      params-map}))
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
                     (re-frame/dispatch [:set-username (:username user)])
                     (re-frame/dispatch [:set-panel :home])))))))

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
  (let [key? (nil? (storage/get-item "session"))]
    (if-not key?
      (auth-with-session (storage/get-item "session")))))
