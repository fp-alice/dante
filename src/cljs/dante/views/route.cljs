(ns dante.views.route
  (:require [accountant.core :as accountant]
            [dante.views.home :as home]
            [dante.views.info :as info]
            [dante.views.login :as login]))

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
