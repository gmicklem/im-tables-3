(ns cljsjs.highlight
  "Stand-in for the cljsjs/highlight jar: sets the global `hljs` that im-tables uses as
  `js/hljs`. The language namespaces under cljsjs.highlight.langs register one language
  each, as the cljsjs jars did. See cljsjs.react-select."
  (:require ["highlight.js/lib/highlight" :as hljs]))

(aset goog/global "hljs" hljs)
