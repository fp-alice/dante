(ns dante.views.login
  (:require [cljs-react-material-ui.reagent :as ui]
            [dante.state.http :as http]
            [re-frame.core :as re-frame]
            [reagent.core :as r]))

(defn login []
  (let [username       (re-frame/subscribe [:login-username])
        password       (re-frame/subscribe [:login-password])
        password-check (re-frame/subscribe [:login-password-check])
        sign-up        (r/atom true)]
    (http/auth-if-session!)
    (fn []
      [ui/paper
       {:class "row content" :style {:overflow-y "auto"}}
       [ui/flat-button {:label    (if-not @sign-up "Switch to sign up page" "Switch to log in page")
                        :on-click #(swap! sign-up not)
                        :style    {:width "100vw"}}]
       (if @sign-up
         [:div.login
          [:div
           [ui/text-field
            {:floating-label-fixed true
             :floating-label-text  "Username"
             :value                @username
             :on-change            #(re-frame/dispatch [:set-login-username (-> % .-target .-value)])}]
           [ui/text-field
            {:floating-label-fixed true
             :floating-label-text  "Password"
             :value                @password
             :type                 "password"
             :on-change            #(re-frame/dispatch [:set-login-password (-> % .-target .-value)])}]
           [ui/text-field
            {:floating-label-fixed true
             :floating-label-text  "Password check"
             :value                @password-check
             :type                 "password"
             :on-change            #(re-frame/dispatch [:set-login-password-check (-> % .-target .-value)])}]
           [:br]
           [ui/raised-button
            {:label    "sign up"
             :on-click #(if (= @password-check @password)
                          (http/sign-up @username @password))
             :class    "loginbutton"}]]]
         [:div.login
          [:div
           [ui/text-field
            {:floating-label-fixed true
             :floating-label-text  "Username"
             :value                @username
             :on-change            #(re-frame/dispatch [:set-login-username (-> % .-target .-value)])}]
           [ui/text-field
            {:floating-label-fixed true
             :floating-label-text  "Password"
             :value                @password
             :type                 "password"
             :on-change            #(re-frame/dispatch [:set-login-password (-> % .-target .-value)])}]
           [:br]
           [ui/raised-button
            {:label    "login"
             :on-click #(if (not-empty @password)
                          (http/get-session @username @password))
             :class    "loginbutton"}]]])])))
