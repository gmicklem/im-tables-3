(ns im-tables.views.table.head.controls
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as reagent]
            [im-tables.views.graphs.histogram :as histogram]
            [im-tables.views.common :refer [no-value]]
            [imcljs.path :as path]
            [clojure.string :as string]
            [clojure.set :as set]
            [oops.core :refer [oget ocall ocall! oget+ oset!]]
            [im-tables.utils :refer [on-event pretty-number display-name place-below! react-select-id?]]
            [goog.string :as gstring]
            ;; sets js/Select, used below
            [cljsjs.react-select]))

;; nil type specified in :applies-to is for classes (i.e., not attributes).
(def operators [{:op "LOOKUP"
                 :label "Lookup"
                 :applies-to #{nil}}
                {:op "IN"
                 :label "In list"
                 :applies-to #{nil}}
                {:op "NOT IN"
                 :label "Not in list"
                 :applies-to #{nil}}
                {:op "ISA"
                 :label "Is a"
                 :applies-to #{nil}}
                {:op "="
                 :label "="
                 :applies-to #{"java.lang.String" "java.lang.Boolean" "java.lang.Integer" "java.lang.Double" "java.lang.Float" "java.util.Date"}}
                {:op "!="
                 :label "!="
                 :applies-to #{"java.lang.String" "java.lang.Boolean" "java.lang.Integer" "java.lang.Double" "java.lang.Float" "java.util.Date"}}
                {:op "<"
                 :label "<"
                 :applies-to #{"java.lang.Integer" "java.lang.Double" "java.lang.Float" "java.util.Date"}}
                {:op "<="
                 :label "<="
                 :applies-to #{"java.lang.Integer" "java.lang.Double" "java.lang.Float" "java.util.Date"}}
                {:op ">"
                 :label ">"
                 :applies-to #{"java.lang.Integer" "java.lang.Double" "java.lang.Float" "java.util.Date"}}
                {:op ">="
                 :label ">="
                 :applies-to #{"java.lang.Integer" "java.lang.Double" "java.lang.Float" "java.util.Date"}}
                {:op "CONTAINS"
                 :label "Contains"
                 :applies-to #{"java.lang.String"}}
                {:op "LIKE"
                 :label "Like"
                 :applies-to #{"java.lang.String"}}
                {:op "NOT LIKE"
                 :label "Not like"
                 :applies-to #{"java.lang.String"}}
                {:op "ONE OF"
                 :label "One of"
                 :applies-to #{"java.lang.String" "java.lang.Boolean"}
                 :multiple-values? true}
                {:op "NONE OF"
                 :label "None of"
                 :applies-to #{"java.lang.String" "java.lang.Boolean"}
                 :multiple-values? true}
                {:op "IS NULL"
                 :label "Null"
                 :applies-to #{"java.lang.String" "java.lang.Boolean" "java.lang.Integer" "java.lang.Double" "java.lang.Float" "java.util.Date"}
                 :no-value? true}
                {:op "IS NOT NULL"
                 :label "Not null"
                 :applies-to #{"java.lang.String" "java.lang.Boolean" "java.lang.Integer" "java.lang.Double" "java.lang.Float" "java.util.Date"}
                 :no-value? true}])

(defn filter-input []
  (fn [loc view val !scale-type]
    [:div.inline-filter
     [:i.fa.fa-filter]
     [:input.form-control
      {:type "text"
       :value val
       :placeholder "Search for a value..."
       :on-change (fn [e]
                    (dispatch [:select/set-text-filter
                               loc
                               view
                               (oget e :target :value)]))}]
     [:button.btn.btn-default.btn-sm
      {:type "button"
       :on-click #(reset! !scale-type
                          (if (= @!scale-type :linear) :log :linear))}
      (name @!scale-type)]]))

(defn force-close
  "Force the bootstrap dropdown that contains the DOM node to close. The node comes
  from the event (`currentTarget`), as reagent.dom/dom-node is gone in reagent 2."
  [node]
  (-> (js/$ node)
      (ocall! "closest" ".dropdown")
      (ocall! "children" ".dropdown-toggle")
      (ocall! "dropdown" "toggle")))

(defn has-text?
  "Return true if a label contains a string"
  [needle haystack]
  (if needle
    (if-some [text-to-search (:item haystack)]
      (re-find (re-pattern (str "(?i)" (gstring/regExpEscape needle))) (str text-to-search))
      false)
    true))

(defn constraint-has-path? [view constraint]
  (= view (:path constraint)))

(defn constraint-dropdown [{:keys [value on-change data-type]}]
  (into [:select.form-control
         {:value (or value "=")
          :on-change (fn [e] (on-change {:op (.. e -target -value)}))}]
        (for [{:keys [op label applies-to]} operators
              :when (contains? applies-to data-type)]
          [:option {:value op} label])))

;; The following input components and code are duplicated from BlueGenes.
;; If we wish to continue using im-tables-3 in the future, we should consider
;; moving these into a library of common components.

(def iso-date #"\d{4}-\d{2}-\d{2}")

(defn date-constraint-input
  "The browser's date input, for a constraint on a date (its value is the
  YYYY-MM-DD string we use as constraint value). A value that is not such a
  date, e.g. one entered for another calendar, is shown in a text input so it
  stays visible and editable."
  [{:keys [value on-change on-blur]}]
  [:input.form-control.date-constraint-input
   {:type (if (or (string/blank? value) (re-matches iso-date value)) "date" "text")
    :placeholder "YYYY-MM-DD"
    :value (or value "")
    :on-change (fn [e] (on-change (oget e :target :value)))
    :on-blur (fn [e] (on-blur (oget e :target :value)))}])

(defn select-placeholder
  [model path]
  (->> (string/split (path/friendly model path) " > ")
       (take-last 2)
       (string/join " > ")
       (str "Choose ")))

(defn select-constraint-input
  "Wraps `cljsjs/react-select` for use as constraint input for selecting
  one value out of `possible-values`."
  [{:keys [model path value on-blur possible-values disabled]}]
  [:> js/Select
   {:className "constraint-select"
    :classNamePrefix "constraint-select"
    ;; The two lines below makes it use a React portal to attach the element to
    ;; the document body. This means it won't disappear at modal-body edges,
    ;; which are set to overflow:auto to facilitate scrolling.
    :styles {:menuPortal (fn [base] (oset! base :zIndex 9999))}
    :menuPortalTarget js/document.body
    :placeholder (select-placeholder model path)
    :isDisabled disabled
    ;; Leaving the line below as it can be useful in the future.
    ; :isLoading (seq? possible-values)
    :onChange (fn [value]
                (on-blur (oget value :value)))
    :value (when (not-empty value) {:value value :label value})
    :options (map (fn [v] {:value (str v) :label (str v)}) (remove nil? possible-values))}])

(defn select-multiple-constraint-input
  "Wraps `cljsjs/react-select` for use as constraint input for selecting
  multiple values out of `possible-values`."
  [{:keys [model path value on-blur possible-values disabled]}]
  [:> js/Select
   {:className "constraint-select"
    :classNamePrefix "constraint-select"
    ;; The two lines below makes it use a React portal to attach the element to
    ;; the document body. This means it won't disappear at modal-body edges,
    ;; which are set to overflow:auto to facilitate scrolling.
    :styles {:menuPortal (fn [base] (oset! base :zIndex 9999))}
    :menuPortalTarget js/document.body
    :placeholder (select-placeholder model path)
    :isMulti true
    :isDisabled disabled
    ;; Leaving the line below as it can be useful in the future.
    ; :isLoading (seq? possible-values)
    :onChange (fn [values]
                (on-blur (not-empty (map :value (js->clj values :keywordize-keys true)))))
    :value (map (fn [v] {:value v :label v}) value)
    :options (map (fn [v] {:value (str v) :label (str v)}) (remove nil? possible-values))}])

(defn text-constraint-input
  "Freeform textbox for String / Lookup constraints."
  []
  (let [focused? (reagent/atom false)]
    (fn [{:keys [value on-change on-blur disabled]}]
      [:input.form-control
       {:data-toggle "none"
        :disabled disabled
        :class (when disabled "disabled")
        :type "text"
        :value value
        :on-focus (fn [e] (reset! focused? true))
        :on-change (fn [e] (on-change (oget e :target :value)))
        :on-blur (fn [e] (on-blur (oget e :target :value)) (reset! focused? false))
        :on-key-down (fn [e] (when (= (oget e :keyCode) 13)
                               (on-blur (oget e :target :value))
                               (reset! focused? false)))}])))

(def operators-no-value
  (set (map :op (filter :no-value? operators))))

(defn constraint-input
  "Returns the appropriate input component for the constraint operator."
  [& {:keys [model path value typeahead? on-change on-blur type
             possible-values disabled op]
      :as props}]
  (cond
    (operators-no-value op)
    nil

    (= type "java.util.Date")
    [date-constraint-input props]

    (and (not= type "java.lang.Integer")
         typeahead?
         (seq? possible-values)
         (#{"=" "!="} op))
    [select-constraint-input props]

    (and typeahead?
         (seq? possible-values)
         (#{"ONE OF" "NONE OF"} op))
    [select-multiple-constraint-input props]

    :else
    [text-constraint-input props]))

(def op-type
  "Map from constraint string to either `:single` or `:multi`, corresponding
  to whether the constraint is for one or multiple values."
  (merge (zipmap (map :op (filter (complement :multiple-values?) operators))
                 (repeat :single))
         (zipmap (map :op (filter :multiple-values? operators))
                 (repeat :multi))))

;; This won't add/remove :code if you switch from/to a type constraint. We just
;; want to support showing them and changing their value; it's currently not
;; possible to add a new type constraint. Once we support that in the future,
;; we can handle the forementioned case.
(defn update-constraint
  "Takes a constraint map and a map with a new value and/or operation, and
  updates the original constraint map, making sure to update the value key if
  switching between single and multi constraints. Also handles type constraints."
  [{old-op :op :as constraint} {new-op :op new-value :value :as new-const}]
  (let [old-op (if (contains? constraint :type) "ISA" old-op)] ; Type constraints have no op.
    (cond-> constraint
      (contains? new-const :op)
      (as-> const
            (cond-> const
              (and (= old-op "ISA") (not= new-op "ISA")) ; Changed away from type constraint.
              (set/rename-keys {:type :value})) ; Type constraints are :single op.
        (case [(op-type old-op) (op-type new-op)]
          [:single :multi] (-> const
                               (set/rename-keys {:value :values})
                               (update :values #(if (empty? %) (list) (list %))))
          [:multi :single] (-> const
                               (set/rename-keys {:values :value})
                               (update :value first))
          const)
        (cond-> const
          (not= new-op "ISA")
          (assoc :op new-op))
        (cond-> const
          (and (not= old-op "ISA") (= new-op "ISA")) ; Changed to a type constraint.
          (-> (dissoc :op) (set/rename-keys {:value :type}))))
      (contains? new-const :value)
      (as-> const
            (if (contains? const :type)
              (assoc const :type new-value)
              (case (op-type (:op const))
                :single (assoc const :value new-value)
                :multi (assoc const :values new-value)))))))

(defn blank-constraint [loc view]
  (let [possible-values (subscribe [:selection/possible-values loc view])
        model (subscribe [:assets/model loc])
        consts (subscribe [:filter-manager/filters loc])]
    (fn [loc view state]
      (let [model (assoc @model :type-constraints @consts)
            submit-constraint (fn []
                                (dispatch [:filters/add-constraint loc @state])
                                (reset! state {:path view :op "=" :value nil}))
            on-constraint-change (fn [new-const]
                                   (swap! state update-constraint new-const))
            on-change (fn [v]
                        (swap! state update-constraint {:value v}))
            data-type (path/data-type model view)
            op (if (:type @state) "ISA" (:op @state))]
        [:div.imtable-constraint
         [:div.constraint-operator
          [constraint-dropdown
           {:value op
            :on-change on-constraint-change
            :data-type data-type}]]
         [:div.constraint-input
          [constraint-input
           :model model
           :path view
           :value (or (:value @state)
                      (:values @state)
                      (:type @state))
           :typeahead? true
           :on-change on-change
           :on-blur on-change
           :type data-type
           :possible-values @possible-values
           :disabled false
           :op op]]]))))

(defn constraint [loc view]
  (let [possible-values (subscribe [:selection/possible-values loc view])
        model (subscribe [:assets/model loc])
        consts (subscribe [:filter-manager/filters loc])]
    (fn [loc view {:keys [path op value values type code] :as const} index]
      (let [model (assoc @model :type-constraints @consts)
            on-constraint-change (fn [new-const]
                                   (dispatch [:filters/update-constraint loc index
                                              (update-constraint const new-const)]))
            on-change (fn [v]
                        (dispatch [:filters/update-constraint loc index
                                   (update-constraint const {:value v})]))
            data-type (path/data-type model view)
            op (if type "ISA" op)]
        [:div.imtable-constraint
         [:div.constraint-operator
          [constraint-dropdown
           {:value op
            :on-change on-constraint-change
            :data-type data-type}]]
         [:div.constraint-input
          [constraint-input
           :model model
           :path path
           :value (or value values type) ; Type is used by type constraints.
           :typeahead? true
           :on-change on-change
           :on-blur on-change
           :type data-type
           :possible-values @possible-values
           :disabled false
           :op op]]
         [:button.btn.btn-raised.btn-danger.constraint-delete
          {:on-click (fn [] (dispatch [:filters/remove-constraint loc const]))
           :type "button"} [:i.fa.fa-times]]]))))

(defn filter-view [loc view blank-constraint-atom]
  (let [query (subscribe [:main/temp-query loc view])]
    (fn [loc view]
      (let [active-filters (for [[i const] (map-indexed vector (:where @query))
                                 :when (constraint-has-path? view const)]
                             [constraint loc view const i])]
        [:form.form.filter-view {:on-submit (fn [e]
                                              (ocall e "preventDefault")
                                              (force-close (oget e :currentTarget)))}
         [:div.alert.alert-success
          (if (seq active-filters)
            (into [:div [:h4 "Active filters:"]] active-filters)
            [:h4 "No active filters"])]
         [:div.alert.alert-default
          [:h4 "Add a new filter:"]
          ; Note that we're storing the blank constraint's
          [blank-constraint loc view blank-constraint-atom]]
         [:div.btn-toolbar.pull-right
          [:div.btn-group
           [:button.btn.btn-default
            {:on-click (fn []
                         (dispatch [:filters/add-constraint loc @blank-constraint-atom])
                         (reset! blank-constraint-atom {:path view :op "=" :value nil}))
             :type "button"} "Add More"]]

          [:div.btn-group
           [:input.btn.btn-primary.pull-right
            {:type "submit"
             :on-click (fn []
                         (when (or (not-empty (:value @blank-constraint-atom))
                                   (not-empty (:values @blank-constraint-atom))
                                   (operators-no-value (:op @blank-constraint-atom)))
                           (dispatch [:filters/add-constraint loc @blank-constraint-atom])
                           (reset! blank-constraint-atom {:path view :op "=" :value nil})))
             ; don't put :data-toggle "dropdown" in here, it stops
             ; the form submitting.... silently. Nice.
             :value "Apply"}]]]]))))

(defn too-many-values []
  (fn []
    [:div.alert.alert-warning
     [:div
      {:style {:padding "10px"}}
      [:h3 "Column summary not available"]
      [:p "Summaries can only be made on columns with 1,000 values or less."]]]))

(def clj-min min)
(def clj-max max)

(defn column-summary-title [loc view response & {:keys [numerical?]}]
  (let [model @(subscribe [:assets/model loc])
        {:keys [results uniqueValues]} response
        human-name (display-name model view)]
    [:h4.title
     (cond
       numerical? (str "Showing numerical distribution for " (pretty-number uniqueValues) " " human-name)
       :else (if (< (count results) uniqueValues)
               (str "Showing " (pretty-number (count results)) " of "
                    (pretty-number uniqueValues) " " human-name)
               (str (pretty-number uniqueValues) " " human-name)))]))

(defn numerical-column-summary []
  (fn [loc view response trimmer]
    (let [results (:results response)
          {:keys [min max average stdev]} (first results)]
      [:form.form.column-summary {:on-submit (fn [e] (ocall e "preventDefault"))}
       [column-summary-title loc view response :numerical? true]
       [histogram/numerical-histogram results @trimmer]
       [:div.main-view
        [:div.numerical-content-wrapper
         [:table.table.table-condensed
          [:thead
           [:tr
            [:th "Min"]
            [:th "Max"]
            [:th "Average"]
            [:th "Std Deviation"]]]
          [:tbody
           [:tr
            [:td (pretty-number min)]
            [:td (pretty-number max)]
            [:td (pretty-number average)]
            [:td (pretty-number stdev)]]]]
         [:div
          [:label "Trim from " [:input {:type "text"
                                        :value (or (:from @trimmer) min)
                                        :on-change (fn [e]
                                                     (swap! trimmer assoc :from (oget e :target :value)))}]]
          [:input {:type "range"
                   :min min
                   :value (or (:from @trimmer) min)
                   :max max
                   :on-change (fn [e] (swap! trimmer assoc :from
                                             (let [new-value (js/parseInt (clj-min (or (:to @trimmer) max) (oget e :target :value)))]
                                               (if (= new-value min) nil new-value))))}]
          [:label "Trim to " [:input {:type "text"
                                      :value (or (:to @trimmer) max)
                                      :placeholder max
                                      :on-change (fn [e]
                                                   (swap! trimmer assoc :to (oget e :target :value)))}]]
          [:input {:type "range"
                   :min min
                   :value (or (:to @trimmer) max)
                   :max max
                   :on-change (fn [e] (swap! trimmer assoc :to
                                             (let [new-value (js/parseInt (clj-max (or (:from @trimmer) min) (oget e :target :value)))]
                                               (if (= new-value max) nil new-value))))}]]]
        [:div.btn-toolbar.column-summary-toolbar
         [:button.btn.btn-primary
          {:type "button"
           :on-click (fn [e]
                       (dispatch [:main/apply-numerical-filter loc view @trimmer])
                       (force-close (oget e :currentTarget))
                       (reset! trimmer {}))}
          [:i.fa.fa-filter]
          (str " Filter")]]]])))

(defn column-summary-thinking []
  ;; The span is what the CSSTransitionGroup (react-transition-group 1) used here
  ;; rendered. Its 50 ms fade never showed: the loader is there from the first
  ;; render, and CSSTransitionGroup only animated children added later.
  [:span
   [:div.column-summary-loader
    [:i.fa.fa-cog.fa-spin.fa-4x.fa-fw]]])

(defn column-summary [loc view local-state !dropdown]
  (let [!scale-type (reagent/atom :linear)]
    (fn [loc view local-state !dropdown]
      (let [response @(subscribe [:selection/response loc view])
            selections @(subscribe [:selection/selections loc view])
            text-filter @(subscribe [:selection/text-filter loc view])]
        (cond
          (nil? response)
          [column-summary-thinking]

          (false? response)
          [too-many-values]

          (contains? (first (:results response)) :min)
          [numerical-column-summary loc view response local-state]

          :else
          [:form.form.column-summary {:on-submit (fn [e] (ocall e "preventDefault"))}
           [column-summary-title loc view response]
           [:div.main-view
            [histogram/main (:results response) @!scale-type]
            [filter-input loc view text-filter !scale-type]
            [:table.table.table-striped.table-condensed.table-scrollable
             [:thead [:tr [:th
                           (if (empty? selections)
                             [:span {:title "Select all"
                                     :on-click (fn [] (dispatch [:select/select-all loc view]))} [:i.fa.fa-check-square-o]]
                             [:span {:title "Deselect all"
                                     :on-click (fn [] (dispatch [:select/clear-selection loc view]))} [:i.fa.fa-square-o]])] [:th.data-item "Item"] [:th "Count"]]]
             (into [:tbody]
                   (->> (filter (partial has-text? text-filter) (:results response))
                        (map (fn [{:keys [count item]}]
                               [:tr.hoverable
                                {:on-click (fn [e] (dispatch [:select/toggle-selection loc view (str item)]))}
                                [:td
                                 [:input
                                  {:on-change (fn [])
                                   :checked (contains? selections (str item))
                                   :type "checkbox"}]]
                                [:td.data-item
                                 (if (some? item)
                                   (str item)
                                   [no-value])]
                                [:td
                                 [:div count]]]))))]]
           [:div.btn-toolbar.column-summary-toolbar
            [:button.btn.btn-primary
             {:type "button"
              :on-click (fn []
                          (dispatch [:main/apply-summary-filter loc view])
                          (when-let [el @!dropdown]
                            (-> (js/$ el)
                                (ocall! "dropdown" "toggle"))))}
             [:i.fa.fa-filter]
             (str " Filter")]]])))))

;; Bootstrap and ReactJS don't always mix well. Components that make up
;; dropdown menus are only mounted (reactjs) once and then their visibility is
;; toggled (bootstrap). These means any local state in the dropdown menu does
;; NOT get reset when the dropdown menu disappears because its never really
;; unmounts.  This was causing the new constraint textbox to retain its value
;; if a user entered something but changed their mind and closed the dropdown.
;; Reopening it would show what they previously entered. Making it look like it
;; had been applied.  To fix this we've create the blank constraint local atom
;; all the way up here at the dropdown level so that we can reset it manually,
;; and then we pass the atom down to the blank constraint component. Lame.
(defn filter-dropdown-menu [loc view]
  (let [possible-values (subscribe [:selection/possible-values loc view])
        query (subscribe [:main/query loc view])
        blank-constraint-atom (reagent/atom {:path view :op "=" :value nil})
        !filter-dropdown (atom nil)
        !dropdown-menu (atom nil)
        last-clicked-id (atom nil)
        listen-last-click (fn [evt]
                            (reset! last-clicked-id (oget evt :target :id)))
        stop-listening (fn []
                         (.removeEventListener js/document "mousedown" listen-last-click)
                         ;; Forget the last click, or a stale react-select id would
                         ;; keep vetoing every later close of this dropdown.
                         (reset! last-clicked-id nil))]
    (fn [loc view right?]
      (let [active-filters? (not-empty (filter (partial constraint-has-path? view) (:where @query)))]
        [:span.dropdown
         {:ref (comp
                (on-event
                 "hide.bs.dropdown"
                 (fn []
                   ;; The Select component is portal'ed to the document body so
                   ;; it won't get affected by the overflow property of the
                   ;; filter menu. To avoid closing the dropdown when clicking
                   ;; the Select component, we check the last clicked element.
                   ;; Nothing has been clicked when the dropdown is closed with the
                   ;; keyboard (Escape) or programmatically, so the id can be nil.
                   (if (react-select-id? @last-clicked-id)
                     false
                     (do (stop-listening)
                         ;; Reset the blank constraint atom when the dropdown is closed
                         (reset! blank-constraint-atom {:path view :op "=" :value nil})
                         ;; *Try* to save the changes every time the dropdown is
                         ;; closed, even by just clicking off it.  This means a user
                         ;; can remove a handful of filters without having to click
                         ;; Apply. The event will do a diff to make sure something
                         ;; has actually changed before rerunning the query
                         (dispatch [:filters/save-changes loc])))))
                (on-event
                 "show.bs.dropdown"
                 (fn []
                   (reset! last-clicked-id nil)
                   (.addEventListener js/document "mousedown" listen-last-click)
                   (when (nil? @possible-values)
                     (dispatch [:main/fetch-possible-values loc view false]))
                   (when-let [dropdown @!dropdown-menu]
                     (when-let [toggle @!filter-dropdown]
                       (place-below! dropdown toggle
                                     :right? right?))))))}
         [:i.fa.fa-filter.dropdown-toggle.filter-icon
          {:on-click (fn [] (dispatch [:main/set-temp-query loc]))
           :data-toggle "dropdown"
           :class (cond active-filters? "active-filter")
           :title (str "Filter " view " column")
           :ref (fn [el] (reset! !filter-dropdown el))}]
         ; Crudely try to draw the dropdown near the middle of the page
         [:div.dropdown-menu
          {:ref (fn [el] (reset! !dropdown-menu el))}
          [filter-view loc view blank-constraint-atom]]]))))

(defn summary-dropdown-menu [loc view]
  (let [local-state (reagent/atom {})
        !summary-dropdown (atom nil)
        !dropdown-menu (atom nil)]
    (fn [loc view right?]
      (let [response @(subscribe [:selection/response loc view])]
        [:span.dropdown
         ;; Bind an event to clear the selected items when the dropdown
         ;; closes. Why don't we just avoid state all together and pick up the
         ;; checkbox values when the user clicks "Filter"? Because we still
         ;; want to know what's selected (for instance, highlighting the
         ;; histogram).
         {:ref (comp
                (on-event
                 "hide.bs.dropdown"
                 (fn []
                   (reset! local-state {})
                   (dispatch [:select/clear-selection loc view])))
                (on-event
                 "show.bs.dropdown"
                 (fn []
                   (when (nil? response)
                     (dispatch [:main/summarize-column loc view]))
                   (when-let [dropdown @!dropdown-menu]
                     (when-let [toggle @!summary-dropdown]
                       (place-below! dropdown toggle
                                     :right? right?
                                     :loading? (nil? response)))))))}
         [:i.fa.fa-bar-chart.dropdown-toggle
          {:title (str "Summarise " view " column")
           :data-toggle "dropdown"
           :ref (fn [el] (reset! !summary-dropdown el))}]
         [:div.dropdown-menu
          {:ref (fn [el] (reset! !dropdown-menu el))}
          [column-summary loc view local-state !summary-dropdown]]]))))

(defn obj->clj [obj]
  (reduce (fn [total next-key]
            (assoc total (keyword next-key) (oget+ obj next-key))) {} (js-keys obj)))

(defn align-right? [dom-node]
  (let [{left :left} (obj->clj (ocall dom-node :getBoundingClientRect))
        screen-width (oget js/window :innerWidth)]
    (> left (/ screen-width 2))))

(defn toolbar [loc view]
  (let [right? (reagent/atom false)]
    (fn [loc view outer-join?]
      (let [sort-direction @(subscribe [:ui/column-sort-direction loc view])
            joined-path? @(subscribe [:query/joined-path? loc view])]
        [:div.summary-toolbar
         {:ref (fn [e]
                 (when e (reset! right? (align-right? e))))}
         (when-not (or outer-join? joined-path?)
           [:i.fa.fa-sort.sort-icon
            {:class (case sort-direction
                      "ASC"  "active-asc-sort"
                      "DESC" "active-desc-sort"
                      nil)
             :on-click (fn [] (dispatch [:main/sort-by loc view]))
             :title (str "Sort " view " column")}])
         [:i.fa.fa-times.remove-icon
          {:on-click (fn [] (dispatch [:main/remove-view loc view]))
           :title (str "Remove " view " column")}]
         [filter-dropdown-menu loc view @right?]
         (when-not joined-path?
           [summary-dropdown-menu loc view @right?])]))))
