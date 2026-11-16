(ns im-tables.views.dashboard.manager.codegen.main
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [clojure.string :as string]
            [imcljs.internal.utils :refer [scrub-url]]
            [oops.core :refer [ocall oget oset!]]
            [cljsjs.highlight]))

;; :hljs is the name the highlight.js language is registered under (see the
;; cljsjs.highlight.langs.* requires in im-tables.core). The code block gets an explicit
;; `language-<name>` class from it: highlight.js 11's auto-detection takes Ruby for Perl.
(def languages {"js" {:label "JavaScript"
                      :hljs "javascript"}
                "pl" {:label "Perl"
                      :hljs "perl"}
                "py" {:label "Python"
                      :hljs "python"
                      :comment "#"}
                "rb" {:label "Ruby"
                      :hljs "ruby"
                      :comment "#"}
                "java" {:label "Java"
                        :hljs "java"}
                "xml" {:label "XML"
                       :hljs "xml"}})

(defn highlight-class
  "The class that tells highlight.js which language `lang` (a key of `languages`) is, e.g.
  \"language-ruby\". The JavaScript generated with `html?` is a whole HTML page, which
  highlight.js knows as xml (it highlights the embedded script and style too)."
  [lang html?]
  (when-let [hljs-name (if (and html? (= "js" lang))
                         "xml"
                         (get-in languages [lang :hljs]))]
    (str "language-" hljs-name)))

(defn options [loc]
  (let [options (subscribe [:codegen/options loc])]
    (fn [loc]
      (let [{:keys [html? comments? lang highlight?]} @options]
        [:div
         [:select.form-control
          {:value lang
           :on-change (fn [e]
                        (dispatch [:main/set-codegen-option loc :lang (oget e :target :value) true]))}
          (map (fn [[value {:keys [label]}]] ^{:key value} [:option {:value value} label]) languages)]
         [:div.codegen-checkbox-container
          (when (= "js" lang)
            [:div [:label [:input
                           {:type "checkbox"
                            :value ""
                            :on-change (fn [e] (dispatch [:main/set-codegen-option loc :html? (not html?)]))
                            :checked html?}] " Include HTML"]])
          [:div [:label [:input
                         {:type "checkbox"
                          :value ""
                          :on-change (fn [e] (dispatch [:main/set-codegen-option loc :comments? (not comments?)]))
                          :checked comments?}] " Include comments"]]
          [:div [:label [:input
                         {:type "checkbox"
                          :value ""
                          :on-change (fn [e] (dispatch [:main/set-codegen-option loc :highlight? (not highlight?)]))
                          :checked highlight?}] " Highlight syntax"]]]]))))

(defn save-to-disk [filename text lang]
  (let [blob (js/Blob. [text] #js {:type "text/plain;charset=utf8"})
        url (-> (ocall js/window :URL.createObjectURL blob))]
    (let [element (ocall js/document :createElement "a")]
      (ocall element :setAttribute "href" url)
      (ocall element :setAttribute "download" (str filename "." lang))
      (oset! element :style :display "none")
      (ocall (oget js/document :body) :appendChild element)
      (ocall element :click)
      (ocall (oget js/document :body) :removeChild element))))

;; highlight.js 11 refuses (with a console message) to highlight an element again unless
;; `dataset.highlighted` is cleared, and it warns about the child elements (spans) that a
;; previous pass left behind. So reduce an already highlighted block to plain text first.
;; The classes a previous pass added (`hljs`, ...) are dropped too, but the explicit
;; `language-<name>` class (see `highlight-class`) must stay, or highlight.js would go back
;; to guessing the language.
(defn- highlight-block! [block]
  (when (oget block :dataset :?highlighted)
    (oset! block :textContent (oget block :textContent))
    (oset! block :className (or (re-find #"language-\S+" (oget block :className)) ""))
    (js-delete (oget block :dataset) "highlighted"))
  (ocall js/hljs :highlightElement block))

(defn modal-body [loc]
  (let [code (subscribe [:codegen/formatted-code loc])
        codegen-settings (subscribe [:codegen/options loc])
        max-height (r/atom nil)]
    (r/create-class
     {:component-did-mount (fn [this]
                              ; Store the max height of the viewport later used to
                              ; set a maximum height on the [:code] block below
                             (reset! max-height (-> js/window js/$ (ocall :height)))
                             (ocall (js/$ "pre code") :each
                                    (fn [i block] (highlight-block! block))))
      :component-did-update (fn [this]
                              (ocall (js/$ "pre code") :each
                                     (fn [i block] (highlight-block! block))))
      :reagent-render (fn [loc]
                        (let [highlight? (:highlight? @codegen-settings)]
                          [:div.container-fluid
                           [:div.row
                            [:div.col-xs-3 [options loc]]
                            [:div.col-xs-9
                             (if (nil? @code)
                               [:pre [:code.nohighlight "Generating code... " [:i.fa.fa-fw.fa-spinner.fa-spin]]]
                                ; Show the code and fix the height of the code container
                                ; to prevent expanding over the viewport
                               (if highlight?
                                 ;; highlight.js replaces the code's text node with spans, so React
                                 ;; must not reconcile (and remove) that node when the code
                                 ;; changes: key the element on its content to get a new one.
                                 [:pre [:code {:key (hash [(:lang @codegen-settings) (:html? @codegen-settings) @code])
                                               :class (highlight-class (:lang @codegen-settings)
                                                                       (:html? @codegen-settings))
                                               :style {:max-height (str (/ @max-height 2) "px")}} @code]]
                                 [:pre {:style {:max-height (str (/ @max-height 2) "px")}} @code]))]]]))})))

(defn modal-footer [loc]
  (let [code (subscribe [:codegen/formatted-code loc])
        options (subscribe [:codegen/options loc])]
    (fn [loc]
      [:div.btn-toolbar.pull-right
       [:button.btn.btn-default
        {:on-click #(dispatch [:modal/close loc])}
        "Close"]
       [:button.btn.btn-raised.btn-primary
        {:on-click (fn [] (save-to-disk "query" @code (:lang @options)))}
        [:i.fa.fa-save] " Download"]])))

(defn build-modal [loc]
  {:header [:h3 "Generate Code"]
   :body [modal-body loc]
   :footer [modal-footer loc]
   :extra-class "codegen-modal"})

(defn main [loc]
  (let [options (subscribe [:codegen/options loc])]
    (fn [loc]
      [:div
       [:div.btn-group
        [:button.btn.btn-default
         {:on-click (fn []
                      (dispatch [:modal/open loc (build-modal loc)])
                      (dispatch [:main/set-codegen-option loc :lang (:lang @options) true]))}
         [:i.fa.fa-code] (str " " (get-in languages [(:lang @options) :label]))]
        [:button.btn.btn-default.dropdown-toggle
         {:data-toggle "dropdown"} [:span.caret]]
        (into [:ul.dropdown-menu]
              (map (fn [[value {:keys [label]}]]
                     [:li {:on-click (fn []
                                       (dispatch [:main/set-codegen-option loc :lang value true])
                                       (dispatch [:modal/open loc (build-modal loc)]))}
                      [:a label]]) languages))]])))
