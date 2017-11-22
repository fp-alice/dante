(ns dante.state.db
  (:require [clojure.string :as string]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent])
  (:require-macros [reagent.ratom :refer [reaction]]))

;Default user state
(def userstate (reagent/atom {:username ""
                              :session  ""
                              :images   []
                              :links    []
                              :key      ""
                              :panel :login
                              :bottom-page 2
                              :pagenum 0
                              :login    {:username       ""
                                         :password       ""
                                         :password-check ""}
                              :script ""}))

(defn map->paths
  "Turns map into vector of deepest nodes"
  ([m] (map->paths [] m))
  ([prev m]
   (reduce-kv (fn [res k v]
                (if (map? v)
                  (into res (map->paths (conj prev k) v))
                  (conj res (conj prev k))))
              []
              m)))

(defn get-key
  "joins `keys` with - and turns into a keyword"
  [keys]
  (keyword (string/join "-" (map name keys))))

(defn set-key
  "prepends `keys` with set and uses get-key"
  [keys]
  (get-key (concat [:set] keys)))

(defn generate-function
  "Makes function for [nodes] in `path`"
  [path]
  (let [get (get-key path)
        set (set-key path)]
    (do
      (re-frame/reg-event-db
       set
       (fn [db [_ value]]
         ;; (println get " : " value)
         (assoc-in db path value)))
      (re-frame/reg-sub-raw
       get
       (fn [db]
         (reaction (get-in @db path)))))))

(re-frame/reg-event-db
 :init
 (fn [_ _]
   @userstate))

;; (re-frame/reg-event-db
;;  :load-defaults
;;  (fn [cofx event]
;;    (let [val (:local-store cofx)
;;          db  (:db cofx)]
;;      {:db (assoc db :defaults val)})))

(re-frame/reg-event-fx
 :load-defaults
 [ (re-frame/inject-cofx :local-store "defaults-key")]     ;; <-- this is new
 (fn [cofx event]
   (let [val (:local-store cofx)
         db  (:db cofx)
         ret (assoc db :defaults val)]
     (println ret)
     (println cofx event)
     {:db ret})))
