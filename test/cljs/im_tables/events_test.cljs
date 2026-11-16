(ns im-tables.events-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [im-tables.events :as events]))

;; The filter UI asked the server for the possible values (two summaryPath requests, a size 1
;; count and then the values) of every attribute it showed a filter for, although the date
;; input is a date picker that ignores them [im-7os.45].
(def model
  {:classes
   {:Gene {:name "Gene"
           :attributes {:symbol {:name "symbol" :type "java.lang.String"}
                        :length {:name "length" :type "java.lang.Integer"}
                        :isPseudo {:name "isPseudo" :type "java.lang.Boolean"}
                        :score {:name "score" :type "java.lang.Double"}
                        :created {:name "created" :type "java.util.Date"}
                        :sequence {:name "sequence"
                                   :type "org.intermine.objectstore.query.ClobAccess"}}
           :references {:organism {:name "organism" :referencedType "Organism"}}}
    :Organism {:name "Organism"
               :attributes {:name {:name "name" :type "java.lang.String"}
                            :established {:name "established" :type "java.util.Date"}}}}})

(def db {:service {:root "http://example.org/mine" :model model}
         :query {:from "Gene" :select ["Gene.symbol"] :where []}
         :temp-query {:from "Gene" :select ["Gene.symbol" "Gene.created"] :where []}})

(defn requested? [view & [temp-query?]]
  (contains? (events/fetch-possible-values-fx db [:test :loc] view temp-query?)
             :im-tables/im-operation))

(deftest possible-values-are-requested-for-enumerable-attributes
  (doseq [view ["Gene.symbol" "Gene.length" "Gene.isPseudo" "Gene.score" "Gene.organism.name"]]
    (is (requested? view) (str view " has a possible values request"))))

(deftest no-possible-values-request-for-date-and-text-attributes
  (testing "Date attributes, also behind a reference, in the query and in the temp query"
    (is (not (requested? "Gene.created")))
    (is (not (requested? "Gene.created" true)))
    (is (not (requested? "Gene.organism.established"))))
  (testing "long text (clob) attributes"
    (is (not (requested? "Gene.sequence")))))

(deftest no-possible-values-request-for-classes
  (is (not (requested? "Gene.organism")))
  (is (not (requested? "Gene"))))

(deftest the-request-stores-its-result-under-the-view
  (let [fx (events/fetch-possible-values-fx db [:test :loc] "Gene.symbol" false)]
    (is (= [:main/store-possible-values [:test :loc] "Gene.symbol"]
           (get-in fx [:im-tables/im-operation :on-success])))
    (is (= db (:db fx)))))
