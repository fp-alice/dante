(ns dante.db
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [monger.gridfs :as gfs :refer [store-file make-input-file
                                           filename content-type
                                           metadata]]
            [digest :as digest]
            [clojure.string :as string]
            [dante.util :refer [frame-text info url]]
            [buddy.hashers :as hs]
            [buddy.sign.jwt :as jwt]
            [buddy.core.keys :as ks]
            [clj-time.core :as t]
            [clojure.java.io :as io]
            [monger.operators :refer :all]
            [monger.result :as res]
            [buddy.sign.util :refer [to-timestamp]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;               /$$     /$$ /$$            ;;
;;              | $$    |__/| $$            ;;
;;   /$$   /$$ /$$$$$$   /$$| $$  /$$$$$$$  ;;
;;  | $$  | $$|_  $$_/  | $$| $$ /$$_____/  ;;
;;  | $$  | $$  | $$    | $$| $$|  $$$$$$   ;;
;;  | $$  | $$  | $$ /$$| $$| $$ \____  $$  ;;
;;  |  $$$$$$/  |  $$$$/| $$| $$ /$$$$$$$/  ;;
;;   \______/    \___/  |__/|__/|_______/   ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility and helper things here           ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def conn
  "Our connection to the local mongo database"
  (mg/connect))

(def fs
  "Our connection to gridfs"
  (mg/get-gridfs conn "dante"))

(def db
  "The Dante collection"
  (mg/get-db conn "dante"))

(defn- mid []
  "Generates a mongo document id"
  (.toString (org.bson.types.ObjectId.)))

(defn- success
  "Create success message to return using optional `status` and `msg`"
  [& {:keys [status msg]
      :or {status  200
           msg "Success!"}}]
  [true {:status status :msg msg}])

(defn- failure
  "Create failure message to return using optional `status` and `msg`"
  [& {:keys [status msg]
      :or   {status  500
             msg "Failed to process"}}]
  [false {:status status :msg msg}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                      ;;
;;   /$$   /$$  /$$$$$$$  /$$$$$$   /$$$$$$  /$$$$$$$   ;;
;;  | $$  | $$ /$$_____/ /$$__  $$ /$$__  $$/$$_____/   ;;
;;  | $$  | $$|  $$$$$$ | $$$$$$$$| $$  \__/  $$$$$$    ;;
;;  | $$  | $$ \____  $$| $$_____/| $$      \____  $$   ;;
;;  |  $$$$$$/ /$$$$$$$/|  $$$$$$$| $$      /$$$$$$$/   ;;
;;   \______/ |_______/  \_______/|__/     |_______/    ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; User logic goes here, read / write / edit stuff, etc ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn clean-user
  "Cleans a user-map of `user` of :password & optional `fields`"
  [user & fields]
  (let [keys (flatten (into [:password :_id] (vec fields)))]
    (apply dissoc user keys)))

(defn find-one-user
  "Finds one user with `query-map`, optional `fields` for selecting
   specific fields. If `fields` is single-indexed return the value"
  [query-map & {:keys [fields exclude]
                :or   {fields  []
                       exclude []}}]
  (let [fields (flatten (vector fields))
        user (mc/find-one-as-map db "users" query-map fields)
        user (clean-user user exclude)]
    (if (= 1 (count fields))
      (get user (first fields))
      user)))

(defn user-exists?
  "Any result for `query-map` in db?"
  [query-map]
  (not (nil? (find-one-user query-map))))

(def pkey
  "Private key and auth string"
  (ks/private-key (io/resource "auth_privkey.pem")
                  (string/trim-newline (slurp ".pw.txt"))))

(defn create-auth-token
  "Creates a token for user `credentials` and uses it as their session key"
  [credentials]
  (let [exp   (-> (t/plus (t/now) (t/days 360)) (to-timestamp))
        map   {:alg :rs256 :exp exp}
        token (jwt/sign credentials pkey map)]
    {:session token}))

(defn update-user [query-map update]
  (mc/update db "users" query-map update))

(defn update-user-token
  "Updates a given `user` to have a new token"
  [user]
  (let [session (:session (create-auth-token user))]
    (update-user user {$set {:session session}})))

(defn make-user
  "Make a user-map we can add to the database using `id` and `credentials`"
  [id credentials]
  (let [user-map (-> credentials
                     (update-in [:password] #(hs/encrypt %))
                     (assoc :_id (str id))
                     (assoc :key id)
                     (assoc :images []))]
    (merge user-map (create-auth-token credentials))))

(defn get-file-md5
  "Gets a file by `md5`"
  [md5]
  (vec (gfs/find-by-md5 fs md5)))

(defn get-user-images
  "Gets all values in [:images] of user-map found with `query-map`
   return ids instead of images if `ids?` true"
  [query-map & {:keys [ids?]
                :or   {ids? false}}]
  (let [image-ids (find-one-user query-map :fields :images)
        images    (apply list (map get-file-md5 image-ids))]
    (if-not (nil? image-ids)
      (if ids?
        image-ids
        (if-not (nil? images)
          images)))))

(defn nuke-user
  "Removes user and their images found with `query-map` from db"
  [query-map]
  (let [images (get-user-images query-map)])
  (mc/remove db "users" query-map))

(defn insert-user
  "Insert `user-map` to db"
  [user-map]
  (mc/insert db "users" user-map))

(defn add-user
  "Add a user to the database after using `credentials` to make their map"
  [credentials]
  (insert-user (make-user (mid) credentials)))

(defn set-user-key
  "Sets a user found with `query-map` to have key `key`"
  [query-map key]
  (update-user query-map {$set {:key key}}))

(defn authenticate-user
  "Checks if `credentials` are valid and return a user-map if they are"
  [credentials]
  (let [username (:username credentials)
        password (:password credentials)
        found    (mc/find-one-as-map db "users" {:username username})]
    (if-not (nil? found)
      (if (hs/check password (:password found))
        found))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                      ;;
;;   /$$$$$$            /$$       /$$ /$$$$$$$$/$$$$$$  ;;
;;  /$$__  $$          |__/      | $$| $$_____/$$__  $$ ;;
;; | $$  \__/  /$$$$$$  /$$  /$$$$$$$| $$    | $$  \__/ ;;
;; | $$ /$$$$ /$$__  $$| $$ /$$__  $$| $$$$$ |  $$$$$$  ;;
;; | $$|_  $$| $$  \__/| $$| $$  | $$| $$__/  \____  $$ ;;
;; | $$  \ $$| $$      | $$| $$  | $$| $$     /$$  \ $$ ;;
;; |  $$$$$$/| $$      | $$|  $$$$$$$| $$    |  $$$$$$/ ;;
;;  \______/ |__/      |__/ \_______/|__/     \______/  ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Manage image storage and files in GridFS             ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn file->md5
  "Morphism for a `file` to its md5"
  [file]
  (digest/md5 file))

(defn file-exists?
  "Checks if a file object `file` exists in the database"
  [file]
  (let [md5   (file->md5 file)
        lista (first (get-file-md5 md5))]
    (not (empty? lista))))

(defn- store
  "Try to store a file `file` by name `name` to user who holds key `key`"
  [file name key]
  (let [user?  (not (nil? (mc/find-one db "users" {:key key})))
        failed (failure :msg "Failed to process store request")
        md5    (file->md5 file)]
    (println user?)
    (if user?
      (let [upload   (store-file (make-input-file fs file)
                                 (filename name)
                                 (metadata {:format "png"})
                                 (content-type "image/png"))
            update   (update-user {:key key} {$push {:images md5}})
            updated? (res/updated-existing? update)
            user     (find-one-user {:key key} :fields :username)]
        (if upload
          (info "Stored image" name md5)
          (info "Failed to store image" name md5))
        (if updated?
          (info "Updated user" user)
          (info "Failed to update user" user))
        {:status 200 :body (str url "i/" md5 ".png")})
      failed)))

(defn store-img
  "Stores an image file using `file` `name` and `key` to identify user"
  [file name key]
  (let [exists? (file-exists? file)
        msg (if exists? "Found file, aborting" "Uploading file")]
    (frame-text "Upload" msg)
    (if exists?
      (let [text-map {:file name :md5 (digest/md5 file)}
            msg    (str "Already found: " text-map " in store")
            failed (failure :status 409 :msg msg)]
        failed)
      (store file name key))))

(defn- remove-file
  "Remove file found by `query-map` from db"
  [query-map]
  (let [file (gfs/find-one-as-map fs query-map)
        md5 (:md5 file)]
    (if-not (nil? md5)
      (let [user (update-user {:images {$in (vec md5)}} {$pull {:images md5}})]
        (info "Removed img from user, deleting file" md5)
        (gfs/remove fs query-map))
      (info "No file found" query-map))))

(defn remove-img
  "Remove image found by `query-map` from db"
  [query-map]
  (remove-file query-map))

(defn remove-img-md5
  "Remove image from db with `md5`"
  [md5]
  (remove-file {:md5 md5}))

(defn remove-img-file
  "Remove image from db with `file`"
  [file]
  (remove-img-md5 (file->md5 file)))

(defn find-map-by-md5
  "Find image-map using `md5`"
  [md5]
  (gfs/find-one-as-map fs {:md5 md5}))
