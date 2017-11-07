(ns dante.views.route
  (:require [dante.views.home :as home]
            [dante.views.login :as login]
            [dante.views.info :as info]))

(defmulti views identity)
(defmethod views :home    [] [home/home])
(defmethod views :login   [] [login/login])
(defmethod views :info    [] [info/info])
(defmethod views :default [] [:div])
