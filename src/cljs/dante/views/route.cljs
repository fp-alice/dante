(ns dante.views.route
  (:require [dante.views.home :as home]
            [dante.views.login :as login]
            [dante.views.info :as info]
            [accountant.core :as accountant]))

(defmulti view identity)
(defmethod view :home    [] [home/home])
(defmethod view :login   [] [login/login])
(defmethod view :info    [] [info/info])
(defmethod view :default [] [:div])

(defn views [panel]
  (accountant/navigate! (str "/#/" (name panel)))
  (view panel))
