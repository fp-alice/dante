(ns dante.db
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [monger.gridfs :as gfs :refer [store-file make-input-file
                                           filename content-type
                                           metadata]]
            [digest :as digest]
            [clojure.string :as string]
            [dante.util :refer [frame-text info]]
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

(defn- success [& {:keys [status
                          msg]
                   :or   {status  200
                          msg "Success!"}}]
  "Create success message to return using optional `status` and `msg`"
  [true {:status status :msg msg}])

(defn- failure [& {:keys [status
                          msg]
                   :or   {status  500
                          msg "Failed to process"}}]
  "Create failure message to return using optional `status` and `msg`"
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

(defn clean-user [user & fields]
  "Cleans a user-map of `user` of :password & optional `fields`"
  (let [keys (flatten (into [:password :_id] (vec fields)))]
    (apply dissoc user keys)))

(defn find-one-user [query-map & {:keys [fields
                                         exclude]
                                  :or   {fields  []
                                         exclude []}}]
  "Finds one user with `query-map`, optional `fields` for selecting specific fields
   if `fields` is single-indexed return the value"
  (let [fields (flatten (vector fields))
        user (mc/find-one-as-map db "users" query-map fields)
        user (clean-user user exclude)]
    (if (= 1 (count fields))
      (get user (first fields))
      user)))

(defn user-exists? [query-map]
  "Any result for `query-map` in db?"
  (not (nil? (find-one-user query-map))))

(def pkey
  "Private key and auth string"
  (ks/private-key (io/resource "auth_privkey.pem") (string/trim-newline (slurp ".pw.txt"))))

(defn create-auth-token [credentials]
  "Creates a token for user `credentials` and uses it as their session key"
  (let [exp   (-> (t/plus (t/now) (t/days 360)) (to-timestamp))
        map   {:alg :rs256 :exp exp}
        token (jwt/sign credentials pkey map)]
    {:session token}))

(defn update-user [query-map update]
  (mc/update db "users" query-map update))

(defn update-user-token [user]
  "Updates a given `user` to have a new token"
  (let [session (:session (create-auth-token user))]
    (update-user user {$set {:session session}})))

(defn make-user [id credentials]
  "Make a user-map we can add to the database using `id` and `credentials`"
  (let [user-map (-> credentials
                     (update-in [:password] #(hs/encrypt %))
                     (assoc :_id (str id))
                     (assoc :key id)
                     (assoc :images []))]
    (merge user-map (create-auth-token credentials))))

(defn get-file-md5 [md5]
  "Gets a file by `md5`"
  (vec (gfs/find-by-md5 fs md5)))

(defn get-user-images [query-map & {:keys [ids?]
                                    :or   {ids? false}}]
  "Gets all values in [:images] of user-map found with `query-map`
   return ids instead of images if `ids?` true"
  (let [image-ids (find-one-user query-map :fields :images)
        images    (apply list (map get-file-md5 image-ids))]
    (if-not (nil? image-ids)
      (if ids?
        image-ids
        (if-not (nil? images)
          images)))))

(defn nuke-user [query-map]
  "Removes user and their images found with `query-map` from db"
  (let [images (get-user-images query-map)])
  (mc/remove db "users" query-map))

(defn insert-user [user-map]
  "Insert `user-map` to db"
  (mc/insert db "users" user-map))

(defn add-user [credentials]
  "Add a user to the database after using `credentials` to make their map"
  (insert-user (make-user (mid) credentials)))

(defn set-user-key [query-map key]
  "Sets a user found with `query-map` to have key `key`"
  (update-user query-map {$set {:key key}}))

(defn authenticate-user [credentials]
  "Checks if `credentials` are valid and return a user-map if they are"
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

(defn file->md5 [file]
  (digest/md5 file))


(defn file-exists? [file]
  "Checks if a file object `file` exists in the database"
  (let [md5   (file->md5 file)
        lista (first (get-file-md5 md5))]
    (not (empty? lista))))

(defn- store [file name key]
  "Try to store a file `file` by name `name` to user who holds key `key`"
  (let [user?  (not (nil? (mc/find-one db "users" {:key key})))
        failed (failure :msg "Failed to process store request")
        md5    (file->md5 file)]
    (if user? (let [upload   (store-file (make-input-file fs file) (filename name)
                                         (metadata {:format "png"}) (content-type "image/png"))
                    update   (update-user {:key key} {$push {:images md5}})
                    updated? (res/updated-existing? update)
                    user     (find-one-user {:key key} :fields :username)]
                (if upload (info "Stored image" name md5) (info "Failed to store image" name md5))
                (if updated? (info "Updated user" user) (info "Failed to update user" user))
                (success :msg (str "Storing file: " {:file name :md5 md5})))
        failed)))

(defn store-img [file name key]
  "Stores an image file using `file` `name` and `key` to identify user"
  (let [exists? (file-exists? file)
        msg (if exists? "Found file, aborting" "Uploading file")]
    (frame-text "Upload" msg)
    (if exists?
      (let [msg    (str "Already found: " {:file name :md5 (digest/md5 file)} " in store")
            failed (failure :status 409 :msg msg)]
        failed)
      (store file name key))))

(defn- remove-file [query-map]
  "Remove file found by `query-map` from db"
  (let [file (gfs/find-one-as-map fs query-map)
        md5 (:md5 file)]
    (if-not (nil? md5)
      (let [user (update-user {:images {$in (vec md5)}} {$pull {:images md5}})]
        (info "Removed img from user, deleting file" md5)
        (gfs/remove fs query-map))
      (info "No file found" query-map))))

(defn remove-img [query-map]
  "Remove image found by `query-map` from db"
  (remove-file query-map))

(defn remove-img-md5 [md5]
  "Remove image from db with `md5`"
  (remove-file {:md5 md5}))

(defn remove-img-file [file]
  "Remove image from db with `file`"
  (remove-img-md5 (file->md5 file)))

(defn find-map-by-md5 [md5]
  (gfs/find-one-as-map fs {:md5 md5}))
