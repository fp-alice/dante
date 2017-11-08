(ns dante.views.home
  (:require [cljs-react-material-ui.reagent :as ui]
            [dante.state.http :as http]
            [re-frame.core :as re-frame]
            [clojure.string :as string]
            [goog.dom :as gdom]
            [goog.events :as gevents]
            [cljs-react-material-ui.icons :as ic])
  (:import [goog.dom query]))

(defn copy-text [text]
  (let [el (js/document.createElement "textarea")
        prev-focus-el js/document.activeElement
        y-pos (or (.. js/window -pageYOffset)
                  (.. js/document -documentElement -scrollTop))]
    (set! (.-style el) #js {:position "absolute"
                            :left "-9999px"
                            :top (str y-pos "px")
                            :fontSize "12pt"
                            :border "0"
                            :padding "0"
                            :margin "0"})
    (set! (.-value el) text)
    (.addEventListener el "focus" (fn [_] (.scrollTo js/window 0 y-pos)))
    (js/document.body.appendChild el)
    (.setSelectionRange el 0 (.. el -value -length))
    (.focus el)
    (js/document.execCommand "copy")
    (.blur el)
    (when prev-focus-el
      (.focus prev-focus-el))
    (.removeAllRanges (.getSelection js/window))
    (js/window.document.body.removeChild el)))

(defn card-from-image [img]
  (let [img  (str img)
        link (str "http://localhost:3000/i/" img)
        page (re-frame/subscribe [:pagenum])]
    (fn []
      [ui/card
      [ui/card-media  {:style {:overflow "hidden"
                               :width "100%"
                               :height "100%"
                               :position "relative"}} ;;  {:style {:position "relative"}}

       ;;Hidden lightbox
       [:a {:href  (str "#/home/")
            :class "lightbox"
            :id    (str "/home/" img)
            :style {:display "hidden"}}
        [:img {:src link
               :href link}]]
       ;;Thumbnail
       [:a {:href  (str "#/home/" img)}
        [:div [:img {:src link
                     :class "thumbnail"
                     :style {:max-height "30vh"}}]]]]
      [ui/flat-button {:on-click #(copy-text link)
                       :label    img
                       :style    {:width    "100%"
                                  :position "relative"
                                  :overflow "visible"}}]])))

(defn home []
  (let [images   (re-frame/subscribe [:images])
        username (re-frame/subscribe [:username])
        session  (re-frame/subscribe [:session])
        key      (re-frame/subscribe [:key])
        pagenum  (re-frame/subscribe [:pagenum])]
    (fn []
      [ui/paper
       {:class "row content"
        :style {:border-radius "0px"}}
       (accountant.core/navigate! (str "/#/home/" @pagenum))
       [ui/flat-button {:label    "Refresh images"
                        :on-click #(http/auth-if-session!)
                        :style    {:width "100vw"}}]

       [:div.image-container
        (let [images (partition-all 20 (remove nil? (vec @images)))
              page   (if (and (number? @pagenum) (not-empty (flatten images))) (nth images (or @pagenum 0)) [])]
          (for [i page] [:div.card-box
                           {:key i}
                           [card-from-image i]]))]])))
