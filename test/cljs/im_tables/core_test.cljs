(ns im-tables.core-test
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures]]
            [im-tables.test-utils :as utils]
            [re-frame.core :as rf]
            [day8.re-frame.test :refer-macros [run-test-sync run-test-async wait-for]]
            [im-tables.events]
            [im-tables.subs]
            [im-tables.test-config :as config]
            [clojure.string :as string]))

(use-fixtures :each utils/fixtures)

(def im-config {:service {:root config/SERVICE-ROOT}
                :query {:from "Gene"
                        :select ["symbol"
                                 "secondaryIdentifier"
                                 "dataSets.description"
                                 "primaryIdentifier"
                                 "organism.name"
                                 "dataSets.name"]
                        :where [{:path "Gene.symbol"
                                 :op "CONTAINS"
                                 :value "abc"}]}
                :settings {:pagination {:limit 10}
                           :links {:vocab {:mine config/MINE-NAME}
                                   :url (fn [{:keys [mine class objectId] :as _vocab}]
                                          (string/join "/" [nil mine "report" class objectId]))}}})

;; The assertions below check what im-tables does with a response (rows, columns, sort
;; order), not which genes a particular mine holds, so the suite passes against any mine with
;; genes whose symbol contains "abc" (HumanMine, FlyMine...). See test_config.cljs.

(defn- rows
  "The rows of the current page, in display order (:results is a map keyed by row index)."
  [response]
  (map val (sort-by key (:results response))))

(defn- column-values [response column]
  (map (fn [row] (:value (first (filter #(= (:column %) column) row))))
       (rows response)))

(deftest load-im-tables
  (let [loc [:default]]
    (utils/after-load loc im-config
      (testing "im-table runs query"
        (let [response @(rf/subscribe [:main/query-response loc])]
          (is (some? response))
          (testing "and stores at least one row, with a cell for every selected column"
            (is (seq (rows response)))
            (is (every? #(= (set (map (partial str "Gene.") (get-in im-config [:query :select])))
                            (set (map :column %)))
                        (rows response)))))))))

(deftest column-summary
  (let [loc [:default]]
    (utils/after-load loc im-config
      (rf/dispatch-sync [:main/summarize-column loc "Gene.dataSets.description"])
      (wait-for [:main/save-column-summary]
        (testing "at least one non-empty column summary"
          (let [summaries @(rf/subscribe [:summaries/column-summaries loc])]
            (is (some (every-pred map? not-empty)
                      (map :response (vals summaries))))))))))

(deftest sort-column
  (let [loc [:default]]
    (utils/after-load loc im-config
      (rf/dispatch-sync [:main/sort-by loc "Gene.primaryIdentifier"])
      (wait-for [:main/replace-query-response]
        (testing "response can be sorted by column"
          (let [response @(rf/subscribe [:main/query-response loc])
                result (get-in response [:results 0])]
            (is (= [{:path "primaryIdentifier" :direction "ASC"}]
                   (:sortOrder @(rf/subscribe [:main/query loc]))))
            (is (some? result))
            ;; was: the first row is FlyMine's "100132289". Any mine: the page is in
            ;; ascending primaryIdentifier order (identifiers compared case-insensitively).
            (let [ids (column-values response "Gene.primaryIdentifier")]
              (is (every? string? ids))
              (is (= ids (sort-by string/lower-case ids))))))))))
