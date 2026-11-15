(defproject org.intermine/im-tables "0.16.0-SNAPSHOT"
  :licence "LGPL-2.1-only"
  :description "ClojureScript library to display and manipulate InterMine query results on a webpage"
  :url "http://www.intermine.org"
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [org.clojure/clojurescript "1.11.132"]
                 [org.clojure/core.async "1.1.587"]
                 [re-frame "1.4.7"]
                 [reagent "1.2.0"]
                 ;; reagent 1.1+ no longer brings React: lein builds take it from cljsjs
                 ;; (deps.edn builds from npm, see package.json).
                 [cljsjs/react "18.3.1-1"]
                 ;; also provides react-dom/client and react-dom/server
                 [cljsjs/react-dom "18.3.1-1"]
                 [cljsjs/highlight "11.7.0-0"]
                 [joshkh/ctrlz "0.3.0"]
                 [binaryage/oops "0.7.0"]
                 [inflections "0.13.2"]
                 ;; lein cannot use git dependencies, so it stays on the last Clojars release; deps.edn
                 ;; pins imcljs 1.7.0-SNAPSHOT by git sha until 1.7.0 is released (gate depcheck
                 ;; compares the artifact name only for git dependencies).
                 [org.intermine/imcljs "1.6.0"]
                 ;; There is no cljsjs build of react-select 5 (deps.edn builds use npm 5.10.2),
                 ;; so lein builds stay on 2.4.4, which still runs on React 18.
                 [cljsjs/react-select "2.4.4-0" :exclusions [cljsjs/react cljsjs/react-dom]]
                 [com.andrewmcveigh/cljs-time "0.5.2"]]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-ancient "0.6.15"]
            [lein-pdo "0.1.1"]
            [lein-cljfmt "0.6.1"]
            [lein-shell "0.5.0"]]

  :cljfmt {:indents {wait-for [[:inner 0]]
                     after-load [[:inner 0]]}}

  :aliases ~(let [compile-less ["npx" "lessc" "--math=always" "less/im-tables.less" "resources/public/css/im-tables.css"]
                  compile-less-prod (conj compile-less "-x")
                  watch-less ["npx" "chokidar" "less/**/*.less" "-c" (clojure.string/join " " compile-less) "--initial"]
                  watch-less-silent (conj watch-less "--silent")]
              {"dev" ["do" "clean,"
                      ["pdo"
                       (into ["shell"] watch-less-silent)
                       ["repl"]]]
               "build" ["do" "clean,"
                        (into ["shell"] compile-less-prod)
                        ["cljsbuild" "once" "min"]]
               "deploy" ["with-profile" "+build" "deploy" "clojars"]
               "format" ["cljfmt" "fix"]
               "kaocha" ["do" "clean,"
                         ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]]
               "less" (into ["shell"] watch-less)})

  :repositories {"clojars" {:sign-releases false}}

  :min-lein-version "2.8.1"

  :source-paths ["src"]

  :test-paths ["test/cljs"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"
                                    "out" "test/js"]

  :figwheel {:css-dirs ["resources/public/css"]
             :server-port 3448}

  :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]
                 :timeout 120000}

  :main im-tables.core

  :profiles {:dev {:dependencies [[binaryage/devtools "1.0.0"]
                                  [day8.re-frame/re-frame-10x "1.12.3"]
                                  [day8.re-frame/tracing "0.6.2"]
                                  [figwheel-sidecar "0.5.20"]
                                  [cider/piggieback "0.4.2"]]
                   :plugins [[lein-figwheel "0.5.19"]]}
             :build {:prep-tasks ["build"]}
             :uberjar {:prep-tasks ["build"]}
             :repl {:source-paths ["dev"]}
             :kaocha {:dependencies [[day8.re-frame/test "0.1.5"]
                                     [lambdaisland/kaocha "1.91.1392"]
                                     [lambdaisland/kaocha-cljs "1.9.181"]]}}

  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src"]
                        :figwheel {:on-jsload "im-tables.core/mount-root"}
                        :compiler {:main im-tables.core
                                   :output-to "resources/public/js/compiled/app.js"
                                   :output-dir "resources/public/js/compiled/out"
                                   :asset-path "js/compiled/out"
                                   :source-map-timestamp true
                                   :parallel-build true
                                   :closure-defines {"re_frame.trace.trace_enabled_QMARK_" true
                                                     "day8.re_frame.tracing.trace_enabled_QMARK_" true}
                                   :preloads [devtools.preload
                                              day8.re-frame-10x.preload]
                                   :external-config {:devtools/config {:features-to-install :all}}}}


                       {:id "min"
                        :source-paths ["src"]
                        :jar true
                        :compiler {:main im-tables.core
                                   :output-to "resources/public/js/compiled/app.js"
                                   :optimizations :advanced
                                   :closure-defines {goog.DEBUG false}
                                   :pretty-print false}}

                       {:id "test"
                        :source-paths ["src" "test/cljs"]
                        :compiler {:main im-tables.runner
                                   :output-to "resources/public/js/compiled/test.js"
                                   :output-dir "resources/public/js/compiled/test/out"
                                   :optimizations :none}}]})
