(ns dante.handler
  (:require [clojure.string :as string]
            [compojure.core :refer [context defroutes GET POST]]
            [compojure.route :refer [not-found resources]]
            [dante.db :as db]
            [dante.middleware :refer [wrap-middleware]]
            [dante.util :refer [in?]]
            [hiccup.page :refer [html5 include-css include-js]]
            [monger.operators :refer :all]
            [ring.util.io :as rio]
            [ring.util.response :as res]))

(def mount-target
  "Mount `div#app` on page to load JS"
  [:div#app
   (comment
     [:h3 "ClojureScript has not been compiled!"]
     [:p "please run "
      [:b "lein figwheel"]
      " in order to start the compiler"])])

(defn head
  "Gets all our included dependencies and sets up the page"
  []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name    "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css "/css/main.css")
   (include-css "//cdn.muicss.com/mui-0.9.27/css/mui.min.css")
   (include-css "https://fonts.googleapis.com/css?family=Roboto")
   (include-js "//cdn.muicss.com/mui-0.9.27/js/mui.min.js")])

(defn loading-page
  "Loads our JS"
  []
  (html5
   (head)
   [:body {:class "body-container"}
    mount-target
    (include-js "/js/app.js")]))

(defn upload
  [request]
  (let [image                              (:image request)
        keyvec                             [:filename :tempfile :size :content-type]
        [file-name file size type :as all] (map #(get image %) keyvec)]
    (if (and (in? ["image/gif" "image/jpeg" "image/png"] type) (< size 25500000))
      (let [res (db/store-img file file-name (:key request))]
        (println res)
        {:status 200
         :body   res})
      {:status 500 :body ("Content type " type " not allowed.")})))

(defn delete-file
  [req]
  (let [img     (:md5 req)
        key     (:key req)
        images  (db/get-user-images {:key key} :ids? true)
        exists? (some? (some #{img} images))]
    (if exists?
      (do (db/update-user {:key key} {$pull {:images img}})
          (db/remove-img-md5 img)
          {:status 200 :body (str "removed image " img)})
      {:status 404 :body "no file or bad key"})))

(defn session
  "Authenticates a user"
  [req]
  (let [user-map {:username (:username req) :password (:password req)}
        user     (db/authenticate-user user-map)]
    (println user)
    {:status 200 :body user}))

(defn make-file-stream
  "Takes an input stream `file` -- such as a MongoDBObject stream and streams it"
  [file]
  (rio/piped-input-stream
   (fn [output-stream]
     (.writeTo file output-stream))))

(defn get-image
  "Gets an image link for the md5 in `req`"
  [req]
  (if (not (nil? (:md5 req)))
    (:status 200 :body {:url (str (:md5 req))})))

(defn download-file-by-id
  "Downloads the requested file with `md5`, if privileges are allowed"
  [md5]
  (let [mongo-file   (first (db/get-file-md5 md5))
        file-map     (db/find-map-by-md5 md5)
        content-type (-> file-map :contentType)
        file-name    (-> file-map :filename)]
    (res/content-type {:status 200 :body (make-file-stream mongo-file)} content-type)))

(defn auth-with-session
  "Authenticate user with session in `req`"
  [req]
  (let [session  (:session req)
        user     (db/find-one-user {:session session})
        response {:status 200 :body {:user user}}]
    response))

(defn sign-up [req]
  (let [name  (:username req)
        pass  (:password req)
        user  (db/add-user {:username name :password pass})
        final (db/find-one-user {:username name})]
    {:status 200 :body {:user final}}))

(defn get-script [req]
  (let [key (:key req)
        url (:url req)
        string (slurp "upload.sh")
        string (string/replace string "USER_KEY" key)]
    {:status 200 :body (string/replace string "SITE_URL" (str url "/api/upload"))}))

(defroutes routes
  (context "/api" []
    (POST "/upload" {params :params} (if (not-any? nil? (map #(get params %) [:image :key]))
                                       (upload params)))
    (POST "/sign-up" {params :params} (sign-up params))
    (POST "/user-session" {params :params} (session params))
    (POST "/img" [req] (get-image req))
    (POST "/auth" {params :params} (auth-with-session params))
    (POST "/script" {params :params} (get-script params))
    (POST "/delete" {params :params} (delete-file params)))
  (GET "/" [] (loading-page))
  (GET "/i/:md5" [md5] (download-file-by-id (first (string/split md5 #"\."))))
  (resources "/")
  (not-found "Not Found"))

;Wrap routes with midddleware
(def app (wrap-middleware #'routes))
