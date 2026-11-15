(ns cljsjs.highlight.langs.java
  "Stand-in for the cljsjs/highlight language pack: registers the java language with hljs."
  (:require [cljsjs.highlight]
            ["highlight.js/lib/languages/java" :as language]))

(js-invoke js/hljs "registerLanguage" "java" language)
