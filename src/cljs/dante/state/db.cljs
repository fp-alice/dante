(ns dante.state.db
  (:require [cljs-time.coerce :refer [to-long]]
            [cljs-time.core :refer [now]]
            [clojure.string :as string]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame])
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
                                         :password-check ""}}))

(defn get-image-coll [images]
  (partition-all 20 images))

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

(defn get-key [keys]
  (keyword (string/join "-" (map name keys))))

(defn set-key [keys]
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
