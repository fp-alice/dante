(ns dante.core
  (:require-macros [secretary.core :refer [defroute]])
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
            [clojure.string :as string]
            [goog.events :as events]
            [dante.util :refer [info]]
            [goog.history.EventType :as EventType])
  (:import goog.History))

(defn get-image-coll [images]
  (partition-all 20 images))

(defn page-frame [username]
  (let [images (re-frame/subscribe [:images])
        num    (re-frame/subscribe [:pagenum])
        panel  (re-frame/subscribe [:panel])]
    (fn [username]
      (let [amt  (dec (count (get-image-coll @images)))
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
                     :title              (str "Dante" (if (and page (= :home @panel)) (str " page " page)))}]))))

(defn set-page [number condition key value]
  (info number condition key value)
  (re-frame/dispatch [:set-bottom-page number])
  (if condition (re-frame/dispatch [key value])))

(defn nav-item [icon label number condition key value]
  [ui/bottom-navigation-item {:icon     icon
                              :label    label
                              :on-click #(set-page number condition key value)}])

(defn bottom-bar [logged-in?]
  (let [num    (re-frame/subscribe [:pagenum])
        images (re-frame/subscribe [:images])
        btn    (re-frame/subscribe [:bottom-page])]
    (fn [logged-in?]
      (let [coll (dec (count (get-image-coll @images)))
            prev (if (>= @num 1) (dec @num) 0)
            next (if (< @num coll) (inc @num) coll)]
        [ui/bottom-navigation {:selected-index @btn}
         (nav-item (ic/av-skip-previous) "first page" 0 (not= @btn 0) :set-pagenum 0)
         (nav-item (ic/av-fast-rewind) "previous page" 1 (> @btn 1) :set-pagenum prev)
         (nav-item (ic/image-photo) "uploads" 2 (not= @btn 2) :set-panel :home)
         (nav-item (ic/action-accessibility) "info" 3 (not= @btn 3) :set-panel :info)
         (nav-item (ic/av-fast-forward) "next page" 4 (not= @btn coll) :set-pagenum next)
         (nav-item (ic/av-skip-next) "last page" 5 (not= @btn coll) :set-pagenum coll)]))))

(defn current-page []
  (let [page     (re-frame/subscribe [:panel])
        username (re-frame/subscribe [:username])]
    (fn []
      [ui/mui-theme-provider
       {:mui-theme (get-mui-theme (aget js/MaterialUIStyles "DarkRawTheme"))}
       [:div.box
        [:div.row-header
         [page-frame @username]]
        (if-not (= "" @username) (route/views @page) (route/views :login))
        [:div.row-footer
         [bottom-bar (= @username "")]]]])))

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defroute #"/#/home/(\d+)" [page]
  (re-frame/dispatch [:set-pagenum (int page)])
  (re-frame/dispatch [:set-panel :home]))

(defroute "/#/home/" []
  (re-frame/dispatch [:set-panel :home]))

(defroute "/#/info" []
  (re-frame/dispatch [:set-panel :info]))

(defroute "/#/login" []
  (re-frame/dispatch [:set-panel :login]))

(defn init! []
  (enable-console-print!)
  (re-frame/dispatch-sync [:init])
  (doseq [keys (db/map->paths @db/userstate)]
    (db/generate-function keys))
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (let [fun   (fn [] (let [p (re-frame/subscribe [:pagenum])]
                           (accountant.core/navigate! (str "/#/home/" @p))))
            check (mapv #(= path %) ["/#/home/" "/#/home"])]
        (if (some true? check) (fun) (secretary/dispatch! path))))
    :path-exists?
    (fn [path]
      (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (mount-root))
