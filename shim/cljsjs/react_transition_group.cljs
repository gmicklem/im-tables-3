(ns cljsjs.react-transition-group
  "Stand-in for the cljsjs/react-transition-group jar: sets the global
  `ReactTransitionGroup` (v1 API) that im-tables.views.table.head.controls uses as
  `js/ReactTransitionGroup.CSSTransitionGroup`. See cljsjs.react-select."
  (:require ["react-transition-group" :as react-transition-group]))

(aset goog/global "ReactTransitionGroup" react-transition-group)
