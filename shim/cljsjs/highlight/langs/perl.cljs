(ns cljsjs.highlight.langs.perl
  "Stand-in for the cljsjs/highlight language pack: registers the perl language with hljs."
  (:require [cljsjs.highlight]
            ["highlight.js/lib/languages/perl" :as language]))

(js-invoke js/hljs "registerLanguage" "perl" language)
