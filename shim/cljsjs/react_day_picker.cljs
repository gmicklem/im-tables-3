(ns cljsjs.react-day-picker
  "Stand-in for the cljsjs/react-day-picker jar: sets the global `DayPicker` (with its
  `Input` property) that im-tables.views.table.head.controls uses as `js/DayPicker.Input`.
  See cljsjs.react-select."
  (:require ["react-day-picker" :as day-picker-module]
            ["react-day-picker/DayPickerInput" :as input-module]))

(let [day-picker (aget day-picker-module "default")]
  (aset day-picker "Input" (aget input-module "default"))
  (aset goog/global "DayPicker" day-picker))
