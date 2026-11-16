(ns im-tables.codegen-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [clojure.string :as string]
            [im-tables.subs :as subs]))

(def query {:from "Gene" :select ["symbol"]})

(defn html-for [root]
  (subs/generate-javascript {:query query
                             :service {:root root}
                             :html? true
                             :comments? false}))

(deftest generated-html-loads-im-tables-from-the-mine
  (testing "the script and stylesheet come from the mine's vendor directory"
    (doseq [root ["https://www.humanmine.org/humanmine"
                  "https://www.humanmine.org/humanmine/"
                  "https://www.humanmine.org/humanmine/service"]]
      (let [html (html-for root)]
        (is (string/includes? html "<script src=\"https://www.humanmine.org/humanmine/vendor/js/intermine/im-tables/2.2.0-beta/imtables.js\""))
        (is (string/includes? html "<link href=\"https://www.humanmine.org/humanmine/vendor/js/intermine/im-tables/2.2.0-beta/main.sandboxed.css\""))
        (is (not (string/includes? html "cdn.intermine.org")))))))

(deftest generated-js-has-no-html
  (let [js (subs/generate-javascript {:query query
                                      :service {:root "https://www.humanmine.org/humanmine"}
                                      :html? false
                                      :comments? false})]
    (is (string/includes? js "require(\"imtables\")"))
    (is (not (string/includes? js "<script")))))
