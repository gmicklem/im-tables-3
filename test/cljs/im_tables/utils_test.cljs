(ns im-tables.utils-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [im-tables.utils :as utils]))

;; The hide.bs.dropdown handler of the column filter dropdown asks
;; react-select-id? about the id of the last element that was clicked. That id is nil when
;; nothing was clicked (Escape, programmatic hide), and used to throw
;; "Cannot read properties of null (reading 'lastIndexOf')" [im-7os.44].
(deftest react-select-id?-is-nil-safe
  (testing "ids of react-select elements keep the dropdown open"
    (is (true? (utils/react-select-id? "react-select-3-option-1")))
    (is (true? (utils/react-select-id? "react-select-2-input"))))
  (testing "no click yet, an element without an id, or another element: close as normal"
    (is (not (utils/react-select-id? nil)))
    (is (not (utils/react-select-id? "")))
    (is (not (utils/react-select-id? "my-input")))
    (is (not (utils/react-select-id? "not-react-select")))))

;; :filters/save-changes runs when a filter dropdown closes; queries parsed from XML have
;; :constraintLogic nil, and trim of nil threw "Cannot read properties of null
;; (reading 'trim')" [im-7os.44].
(deftest ensure-constraint-logic-is-nil-safe
  (let [constraints [{:path "Gene.symbol" :op "=" :value "a" :code "A"}
                     {:path "Gene.length" :op ">" :value "1" :code "B"}]]
    (testing "nil, empty and blank logic are regenerated from the constraints"
      (is (= "A and B" (utils/ensure-constraint-logic nil constraints)))
      (is (= "A and B" (utils/ensure-constraint-logic "" constraints)))
      (is (= "A and B" (utils/ensure-constraint-logic "  " constraints))))
    (testing "no constraints and no logic gives the empty logic"
      (is (= "" (utils/ensure-constraint-logic nil [])))
      (is (= "" (utils/ensure-constraint-logic nil nil))))
    (testing "an existing logic is kept, trimmed"
      (is (= "A or B" (utils/ensure-constraint-logic "  A or B " constraints))))))
