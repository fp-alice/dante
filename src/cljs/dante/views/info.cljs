(ns dante.views.info
  (:require [cljs-react-material-ui.reagent :as ui]
            [clojure.string :as string]
            [re-frame.core :as re-frame]
            [dante.state.http :as http]))

(defn info []
  (let [name   (re-frame/subscribe [:username])
        images (re-frame/subscribe [:images])
        key    (re-frame/subscribe [:key])
        url    (first (string/split (str (.-location js/window)) #"#"))
        script (re-frame/subscribe [:script])]
    (fn []
      (when (= "" @script) (http/get-script @key url))
      [ui/card {:expanded true
                :class    "row-content"
                :style    {:border-radius "0px"}}
       [ui/card-header {:title    @name
                        :subtitle (str (count @images) " images uploaded")}
        [ui/card-text
         [:h3 "Hello " @name ", your key is"]
         [:code @key]
         [:h3 "Here's an example upload script"]
         [:code {:style {:white-space "pre-wrap"}}
          @script]]]])))
