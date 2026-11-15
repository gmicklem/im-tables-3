(ns cljsjs.react-select
  "Stand-in for the cljsjs/react-select jar (see deps.edn: the :cljs alias puts shim/ on the
  classpath instead). The cljsjs jar exposed the library as the browser global `Select`,
  with the creatable variant as `Select.Creatable`, which im-tables.views.table.head.controls
  still uses as `js/Select`. This namespace gets the library from npm and sets that global.
  Since react-select 3 the creatable variant is a separate entry point, react-select/creatable.

  Properties are read with aget/aset on purpose: advanced compilation must not rename them."
  (:require ["react-select" :as react-select]
            ["react-select/creatable" :as creatable]))

(let [select (aget react-select "default")]
  (aset select "Creatable" (aget creatable "default"))
  (aset goog/global "Select" select))
