(ns dante.views.info
  (:require [cljs-react-material-ui.reagent :as ui]
            [dante.state.http :as http]
            [re-frame.core :as re-frame]
            [clojure.string :as string]
            [goog.dom :as gdom]
            [goog.events :as gevents]
            [cljs-react-material-ui.icons :as ic]))

(defn info []
  (let [name   (re-frame/subscribe [:username])
        images (re-frame/subscribe [:images])
        token  (re-frame/subscribe [:key])
        url    (first (string/split (str (.-location js/window)) #"#"))]
    (fn []
      [ui/card {:expanded true
                :class    "row-content"
                :style    {:border-radius "0px"}}
       [ui/card-header {:title    @name
                        :subtitle (str (count @images) " images uploaded")}
        [ui/card-text
         [:h3 "Hello " @name ", your key is"]
         [:code @token]
         [:h3 "Here's an example upload script"]
         [:code {:style {:white-space "pre-wrap"}}
          (string/join "\n" ["#!/bin/sh"
                             "tmp=$(mktemp /tmp/XXXXXXXXXXXXXXXXXXX.png)"
                             "xclip -i -selection clipboard -t text/uri-list $tmp"
                             "sleep 0.2;"
                             "gnome-screenshot -a -f $tmp"
                             "tmpsize=$(wc -c <\"$tmp\")"
                             "if [ $tmpsize != 0 ]; then"
                             (str "out=$(curl -X POST -H \"content-type: multipart/form-data\" "
                                  url "upload -F \"key=" @token "\" -F \"image=@$tmp\")")
                             "final=$(sed -e 's/^\"//' -e 's/\"$//' <<<\"$out\")"
                             "echo $final | xclip -selection clipboard"
                             "xdg-open $final"
                             "fi"])]]]])))
