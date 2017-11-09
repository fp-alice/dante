(defproject dante "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [ring-server "0.5.0"]
                 [reagent "0.7.0"]
                 [reagent-utils "0.2.1"]
                 [ring "1.6.3"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-defaults "0.3.1"]
                 [compojure "1.6.0"]
                 [hiccup "1.0.5"]
                 [yogthos/config "0.9"]
                 [com.novemberain/monger "3.1.0"]
                 [org.clojure/clojurescript "1.9.946"
                  :scope "provided"]
                 [com.google.guava/guava "23.0"]
                 [secretary "1.2.3"]
                 [venantius/accountant "0.2.3"
                  :exclusions [org.clojure/tools.reader]]
                 [cljsjs/material-ui "0.19.2-0"]
                 [cljs-react-material-ui "0.2.50"]
                 [cljsjs/react "16.0.0-0"]
                 [cljsjs/react-dom "16.0.0-0"]
                 [re-frame "0.10.2"]
                 [com.andrewmcveigh/cljs-time "0.5.1"]
                 [digest "1.4.6"]
                 [buddy "2.0.0"]
                 [rid3 "0.2.0"]
                 [buddy/buddy-sign "2.2.0"]
                 [clj-time "0.14.0"]
                 [cljs-http "0.1.44"]]

  :plugins [[lein-environ "1.0.2"]
            [lein-cljsbuild "1.1.5"]
            [lein-asset-minifier "0.2.7"
             :exclusions [org.clojure/clojure]]]

  :ring {:handler dante.handler/app
         :uberwar-name "dante.war"}

  :min-lein-version "2.5.0"

  :uberjar-name "dante.jar"

  :main dante.server

  :clean-targets ^{:protect false}
  [:target-path
   [:cljsbuild :builds :app :compiler :output-dir]
   [:cljsbuild :builds :app :compiler :output-to]]

  :source-paths ["src/clj" "src/cljc"]
  :resource-paths ["resources" "target/cljsbuild"]

  :minify-assets
  {:assets
   {"resources/public/css/site.min.css" "resources/public/css/site.css"}}

  :cljsbuild
  {:builds {:min
            {:source-paths ["src/cljs" "src/cljc" "env/prod/cljs"]
             :compiler
             {:output-to        "target/cljsbuild/public/js/app.js"
              :output-dir       "target/cljsbuild/public/js"
              :source-map       "target/cljsbuild/public/js/app.js.map"
              :optimizations :advanced
              :pretty-print  false}}
            :app
            {:source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
             :figwheel {:on-jsload "dante.core/mount-root"}
             :compiler
             {:main "dante.dev"
              :asset-path "/js/out"
              :output-to "target/cljsbuild/public/js/app.js"
              :output-dir "target/cljsbuild/public/js/out"
              :source-map true
              :optimizations :none
              :pretty-print  true}}



            }
   }


  :figwheel
  {:http-server-root "public"
   :server-port 3449
   :nrepl-port 7002
   :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]
   :css-dirs ["resources/public/css"]
   :ring-handler dante.handler/app}



  :profiles {:dev {:dependencies [[binaryage/devtools "0.9.7"]
                                  [ring/ring-mock "0.3.1"]
                                  [ring/ring-devel "1.6.3"]
                                  [prone "1.1.4"]
                                  [figwheel-sidecar "0.5.14"]
                                  [com.cemerick/piggieback "0.2.2"]
                                  [leiningen-core "2.8.1"]
                                  [org.clojure/tools.nrepl "0.2.13"]
                                  [pjstadig/humane-test-output "0.8.3"]]

                   :repl-options {:init-ns          dante.repl
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

                   :source-paths ["env/dev/clj"]
                   :plugins [[lein-figwheel "0.5.14"]
                             ]

                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]

                   :env {:dev true}}

             :uberjar {:hooks [minify-assets.plugin/hooks]
                       :source-paths ["env/prod/clj"]
                       :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
                       :env {:production true}
                       :aot :all
                       :omit-source true}})
