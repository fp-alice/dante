(ns ^:figwheel-no-load dante.dev
  (:require
    [dante.core :as core]
    [devtools.core :as devtools]))

(devtools/install!)

(enable-console-print!)

(core/init!)
