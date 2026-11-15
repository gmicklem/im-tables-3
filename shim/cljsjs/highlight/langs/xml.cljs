(ns cljsjs.highlight.langs.xml
  "Stand-in for the cljsjs/highlight language pack: registers the xml language with hljs."
  (:require [cljsjs.highlight]
            ["highlight.js/lib/languages/xml" :as language]))

(js-invoke js/hljs "registerLanguage" "xml" language)
