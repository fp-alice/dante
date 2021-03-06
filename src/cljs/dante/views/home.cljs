(ns dante.views.home
  (:require [cljs-react-material-ui.reagent :as ui]
            [dante.state.http :as http]
            [re-frame.core :as re-frame]
            [clojure.string :as string]
            [goog.dom :as gdom]
            [goog.events :as gevents]
            [accountant.core :as accountant]
            [cljs-react-material-ui.icons :as ic]
            [dante.util :refer [url]]
            [reagent.core :as reagent])
  (:import [goog.dom query]))

(defn copy-text [text]
  "Copies `text` to keyboard"
  (let [el            (js/document.createElement "textarea")
        prev-focus-el js/document.activeElement
        y-pos         (or (.. js/window -pageYOffset)
                          (.. js/document -documentElement -scrollTop))]
    (set! (.-style el) #js {:position "absolute"
                            :left     "-9999px"
                            :top      (str y-pos "px")
                            :fontSize "12pt"
                            :border   "0"
                            :padding  "0"
                            :margin   "0"})
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
  "Makes a card for the `img`"
  (let [img   (str img)
        link  (str url "i/" img ".png")
        page  (re-frame/subscribe [:pagenum])
        key   (re-frame/subscribe [:key])
        state (reagent/atom false)]
    (fn [img]
      [ui/card
       [ui/card-media  {:class    "card-media" :style {:overflow "hidden"}
                        :width    "100%"
                        :height   "100%"
                        :position "relative"} ;;  {:style {:position "relative"}}
        ;;Hidden lightbox
        [:div
         [:a {:href  (str "#/home/")
              :class "lightbox"
              :id    (str "/home/" img)
              :style {:display "hidden"}}
          [:img {:src  link
                 :href link}]]

         ;;Thumbnail
         [:a {:href (str "#/home/" img)}
          [:div {:position "relative"}
           [:img {:src   link
                  :class "blur"}]
           [:img {:src   link
                  :class "thumbnail"}]]]]]
       [ui/card-actions {:class "card-action"}
        [ui/flat-button {:on-click #(copy-text link)
                         :icon     (ic/content-link)
                         :label    "link"
                         :class    "card-button"}]
        [ui/flat-button {:on-click #(if (not @state)
                                      (swap! state not)
                                      (http/delete-img @key img))
                         :icon     (ic/action-delete)
                         :label    (if @state "click to confirm" "delete")
                         :class    "card-button"}]]])))

(defn home []
  "Shows the home page"
  (let [images   (re-frame/subscribe [:images])
        username (re-frame/subscribe [:username])
        session  (re-frame/subscribe [:session])
        key      (re-frame/subscribe [:key])
        pagenum  (re-frame/subscribe [:pagenum])]
    (fn []
      [ui/paper {:class "row-content"}
       (accountant/navigate! (str "/#/home/" @pagenum))
       [ui/flat-button {:label    "Refresh images"
                        :on-click #(http/auth-if-session!)
                        :class    "fw-button"}]

       [:div.image-container
        (let [images (partition-all 20 (reverse (remove nil? (vec @images))))
              cond   (and (number? @pagenum) (not-empty (flatten images)))
              index  (or @pagenum 0)
              page   (if cond (nth images index) [])]
          (for [i page] [:div.card-box
                         {:key i}
                         [card-from-image i]]))]])))
