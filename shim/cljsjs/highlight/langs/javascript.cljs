(ns cljsjs.highlight.langs.javascript
  "Stand-in for the cljsjs/highlight language pack: registers the javascript language with hljs."
  (:require [cljsjs.highlight]
            ["highlight.js/lib/languages/javascript" :as language]))

(js-invoke js/hljs "registerLanguage" "javascript" language)
