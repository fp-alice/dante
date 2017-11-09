(ns dante.handler
  (:require [clojure.java.io :as io]
            [compojure.core :refer [defroutes GET POST context]]
            [compojure.route :refer [not-found resources]]
            [dante.middleware :refer [wrap-middleware]]
            [hiccup.page :refer [html5 include-css include-js]]
            [dante.util :refer [frame-text info in?]]
            [dante.db :as db]
            [ring.util.io :as rio]
            [ring.util.response :as r]
            [monger.gridfs :as gfs]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.conversion :as c]
            [ring.util.response :as res]
            [clojure.string :as string])
  (:import [com.mongodb DB DBObject]
           org.bson.types.ObjectId
           [com.mongodb.gridfs GridFS GridFSInputFile]
           [java.io InputStream ByteArrayInputStream File]))

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
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css "/css/site.css")
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
    (clojure.pprint/pprint request)
    (clojure.pprint/pprint all)
    (if (and (in? ["image/gif" "image/jpeg" "image/png"] type) (< size 50000))
      (let [res (db/store-img file file-name (:key request))]
        (println res)
        {:status 200
         :body   res}) {:status 500 :body ("Content type " type " not allowed.")})))

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
        content-type (-> file-map :metadata :contentType)
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

(defroutes routes
  (context "/api" []
           (POST "/upload" {params :params} (if (not-any? nil? (map #(get params %) [:image :key]))
                                              (upload params)))
           (POST "/sign-up" {params :params} (sign-up params))
           (POST "/user-session" {params :params} (session params))
           (POST "/img" [req] (get-image req))
           (POST "/auth" {params :params} (auth-with-session params)))
  (GET "/" [] (loading-page))
  (GET "/i/:md5" [md5] (download-file-by-id (first (string/split md5 #"\."))))
  (resources "/")
  (not-found "Not Found"))

;Wrap routes with midddleware
(def app (wrap-middleware #'routes))
