(ns cljsjs.highlight.langs.ruby
  "Stand-in for the cljsjs/highlight language pack: registers the ruby language with hljs."
  (:require [cljsjs.highlight]
            ["highlight.js/lib/languages/ruby" :as language]))

(js-invoke js/hljs "registerLanguage" "ruby" language)
