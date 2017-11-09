(ns dante.views.route
  (:require [dante.views.home :as home]
            [dante.views.login :as login]
            [dante.views.info :as info]
            [accountant.core :as accountant]
            [reagent.core :as reagent]))

(defmulti view identity)
(defmethod view :home    [] [home/home])
(defmethod view :login   [] [login/login])
(defmethod view :info    [] [info/info])
(defmethod view :default [] [:div])

(defn views
  "navigates to `panel` and changes url"
  [panel]
  (accountant/navigate! (str "/#/" (name panel)))
  (view panel))
