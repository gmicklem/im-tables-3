(ns im-tables.codegen-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [clojure.string :as string]
            [im-tables.subs :as subs]
            [im-tables.views.dashboard.manager.codegen.main :as codegen]
            [cljsjs.highlight]
            [cljsjs.highlight.langs.javascript]
            [cljsjs.highlight.langs.perl]
            [cljsjs.highlight.langs.python]
            [cljsjs.highlight.langs.ruby]
            [cljsjs.highlight.langs.java]
            [cljsjs.highlight.langs.xml]))

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

;; highlight.js 11 detected generated Ruby as Perl, so the code block names its language
;; explicitly [im-7os.23].
(deftest highlight-class-names-the-language
  (testing "each language of the dropdown maps to its highlight.js language"
    (is (= {"js" "language-javascript"
            "pl" "language-perl"
            "py" "language-python"
            "rb" "language-ruby"
            "java" "language-java"
            "xml" "language-xml"}
           (into {} (map (fn [lang] [lang (codegen/highlight-class lang false)])
                         (keys codegen/languages))))))
  (testing "JavaScript with the HTML page around it is xml for highlight.js"
    (is (= "language-xml" (codegen/highlight-class "js" true)))
    (is (= "language-ruby" (codegen/highlight-class "rb" true))))
  (testing "an unknown language gets no class (auto-detection)"
    (is (nil? (codegen/highlight-class "cobol" false)))))

(deftest highlight-classes-are-registered-languages
  (doseq [lang (keys codegen/languages)
          :let [hljs-name (subs (codegen/highlight-class lang false) (count "language-"))]]
    (is (some? (.getLanguage js/hljs hljs-name)) (str hljs-name " is registered"))))
