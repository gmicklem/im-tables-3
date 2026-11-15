(ns cljsjs.highlight.langs.python
  "Stand-in for the cljsjs/highlight language pack: registers the python language with hljs."
  (:require [cljsjs.highlight]
            ["highlight.js/lib/languages/python" :as language]))

(js-invoke js/hljs "registerLanguage" "python" language)
