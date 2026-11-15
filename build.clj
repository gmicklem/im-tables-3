(ns build
  "Build tasks for the im-tables library jar. The jar holds the ClojureScript sources (no AOT,
  no compiled javascript: consumers compile the library themselves) and the compiled
  stylesheet public/css/im-tables.css, which BlueGenes copies out of the jar
  (bluegenes.prep/copy-im-tables-css).

    clojure -T:build clean
    clojure -T:build css       ; resources/public/css/im-tables.css (needs `npm ci` first)
    clojure -T:build jar       ; css, then target/im-tables-<version>.jar (+ pom.xml inside)
    clojure -T:build install   ; jar, then install into ~/.m2
    clojure -T:build:deploy deploy
                               ; jar, then deploy to Clojars (human step; needs
                               ; CLOJARS_USERNAME and CLOJARS_PASSWORD (a deploy token))

  The version is read from the VERSION file."
  (:require [clojure.string :as str]
            [clojure.tools.build.api :as b]))

(def lib 'org.intermine/im-tables)
(def version (str/trim (slurp "VERSION")))
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def css-file "resources/public/css/im-tables.css")

;; :root nil and :user nil: only deps.edn counts, so that the pom does not pick up the
;; org.clojure/clojure dependency of the Clojure CLI's built-in root deps.edn.
(defn- basis [] (b/create-basis {:project "deps.edn" :root nil :user nil}))

(defn clean [_]
  (b/delete {:path "target"})
  (b/delete {:path "resources/public/css"}))

(defn css
  "Compile less/im-tables.less with the lessc from node_modules (the same command as
  the old `lein build`: minified). --math=always because the stylesheet divides without
  parentheses (`@spacer/2`), which lessc 4 only evaluates with that option."
  [_]
  (b/process {:command-args ["npx" "lessc" "--math=always" "-x" "less/im-tables.less" css-file]})
  (println "Compiled" css-file))

(defn jar [_]
  (css nil)
  (b/delete {:path class-dir})
  (b/delete {:path jar-file})
  (let [basis (basis)]
    (b/write-pom {:class-dir class-dir
                  :lib       lib
                  :version   version
                  :basis     basis
                  :scm       {:url                 "https://github.com/intermine/im-tables-3"
                              :connection          "scm:git:git://github.com/intermine/im-tables-3.git"
                              :developerConnection "scm:git:ssh://git@github.com/intermine/im-tables-3.git"
                              :tag                 (str "v" version)}
                  :pom-data  [[:description "ClojureScript library to display and manipulate InterMine query results on a webpage"]
                              [:url "http://www.intermine.org"]
                              [:licenses
                               [:license
                                [:name "LGPL-2.1-only"]]]]})
    (b/copy-dir {:src-dirs (:paths basis)
                 :target-dir class-dir})
    ;; Only the stylesheet from resources/: index.html is the demo page and js/ is build output.
    (b/copy-dir {:src-dirs   ["resources/public/css"]
                 :target-dir (str class-dir "/public/css")})
    (b/jar {:class-dir class-dir
            :jar-file  jar-file})
    (println "Created" jar-file)))

(defn install [_]
  (jar nil)
  (b/install {:basis     (basis)
              :lib       lib
              :version   version
              :jar-file  jar-file
              :class-dir class-dir})
  (println "Installed" (str lib " {:mvn/version \"" version "\"}") "into ~/.m2"))

(defn deploy [_]
  (when (str/ends-with? version "-SNAPSHOT")
    (println "Deploying a SNAPSHOT version:" version))
  (jar nil)
  ((requiring-resolve 'deps-deploy.deps-deploy/deploy)
   {:installer :remote
    :artifact  jar-file
    :pom-file  (b/pom-path {:lib lib :class-dir class-dir})}))
