(ns dante.core
  (:require [dante.state.db]
            [cljsjs.material-ui]
            [reagent.core :as reagent :refer [atom]]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]
            [cljs-react-material-ui.core :refer [get-mui-theme color]]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
            [re-frame.core :as re-frame]
            [dante.state.db :as db]
            [dante.state.http :as http]
            [dante.views.route :as route]
            [clojure.string :as string]))

(defn get-image-coll [images]
  (partition-all 20 images))

(defn page-frame [username]
  (let [images (re-frame/subscribe [:images])
        num (re-frame/subscribe [:pagenum])]
    (fn [username]
      (let [amt (dec (count (get-image-coll @images)))
            page (if (not (empty? @images)) (str " " @num "/" amt) "")]
        [ui/app-bar {:icon-element-right (reagent/as-element

                                          (if (= username "")
                                            [ui/flat-button
                                             {:icon     (ic/action-account-box)
                                              :label    "Log in"
                                              :on-click #(re-frame/dispatch [:set-panel :login])}]
                                            [ui/flat-button
                                             {:icon  (ic/action-account-box)
                                              :label username}]))
                     :title (str "Dante" page)}]))))

(defn set-page [i condit k v & page]
  (fn []
    (let [set-pg (first page)
         set-pg (if set-pg set-pg 2)]
     (re-frame/dispatch [:set-bottom-page i])
     (if condit (re-frame/dispatch [k v]))
     (re-frame/dispatch [:set-bottom-page set-pg]))))

(defn nav-item [icon label num condit k v & page]
  (fn [icon label num condit k v & page]
    (let [pg (first page)]
      [ui/bottom-navigation-item {:icon     icon
                                  :label    label
                                  :on-click (set-page num condit k v (if pg pg num))}])))

(defn bottom-bar [logged-in?]
  (let [index  (re-frame/subscribe [:bottom-page])
        num    (re-frame/subscribe [:pagenum])
        images (re-frame/subscribe [:images])]
    (fn [logged-in?]
      (let [coll  (dec (count (db/get-image-coll @images)))
            home? (= @num 2)]
        [ui/bottom-navigation {:selected-index @index}
         [nav-item (ic/av-skip-previous)     "first page"    0 (not= @num 0)    :set-pagenum 0]
         [nav-item (ic/av-fast-rewind)       "previous page" 1 (>    @num 0)    :set-pagenum (dec @num)]
         [nav-item (ic/image-photo)          "uploads"       2 (not= @index 2)  :set-panel   :home]
         [nav-item (ic/action-accessibility) "info"          3 (not= @index 3)  :set-panel   :info]
         [nav-item (ic/av-fast-forward)      "next page"     4 (not= @num coll) :set-pagenum (inc @num)]
         [nav-item (ic/av-skip-next)         "last page"     5 (not= @num coll) :set-pagenum coll]]))))

(defn current-page []
  (let [page (re-frame/subscribe [:panel])
        username (re-frame/subscribe [:username])]
    [ui/mui-theme-provider
     {:mui-theme (get-mui-theme (aget js/MaterialUIStyles "DarkRawTheme"))}
     [:div.box
      [:div
       {:class "row header"
        :style {:position "static"}}
       [page-frame @username]]
      (route/views @page)
      [:div
       {:class "row footer"
        :style {:position "static"}}
       [bottom-bar (= @username "")]]]]))

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (enable-console-print!)
  (re-frame/dispatch-sync [:init])
  (doseq [keys (db/map->paths @db/userstate)]
    (db/generate-function keys))
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (secretary/dispatch! path))
    :path-exists?
    (fn [path]
      (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (mount-root))

(init!)
