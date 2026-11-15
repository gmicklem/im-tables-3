(ns cljsjs.react-select
  "Stand-in for the cljsjs/react-select jar (see deps.edn: the :cljs alias puts shim/ on the
  classpath instead). The cljsjs jar exposed the library as the browser global `Select`,
  which im-tables.views.table.head.controls still uses as `js/Select`. This namespace gets
  the same library (same version) from npm and sets that global, so the sources are the
  same under lein (cljsjs jar) and shadow-cljs (npm).

  Properties are read with aget/aset on purpose: advanced compilation must not rename them."
  (:require ["react-select" :as react-select]))

(let [select (aget react-select "default")]
  (aset select "Creatable" (aget react-select "Creatable"))
  (aset goog/global "Select" select))
