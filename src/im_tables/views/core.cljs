(ns im-tables.views.core
  (:require [re-frame.core :refer [subscribe dispatch dispatch-sync]]
            [reagent.core :as reagent]
            [im-tables.views.dashboard.main :as dashboard]
            [im-tables.views.table.core :as table]
            [im-tables.components.bootstrap :refer [modal]]
            [reagent.dom.server :as server]
            [oops.core :refer [ocall oget]]
            [imcljs.path :as impath]
            [im-tables.views.table.error :as error]
            [im-tables.utils :refer [unsafe-html]]))

(defn custom-modal []
  (fn [loc {:keys [header body footer extra-class no-fade]}]
    [:div.im-modal
     (if no-fade
       {:class :no-fade}
       {:on-mouse-down #(dispatch [:modal/close loc])})
     [:div.im-modal-content
      {:class extra-class
       :on-mouse-down (fn [e]
                        (ocall e :stopPropagation))}
      [:div.modal-dialog.
       [:div.modal-content
        [:div.modal-header header]
        [:div.modal-body body]
        [:div.modal-footer footer]]]]]))

(defn constraint-has-path? [view constraint]
  (= view (:path constraint)))

(defn error-boundary
  [props & children]
  (let [caught? (reagent/atom false)
        !error (reagent/atom nil)]
    (reagent/create-class
     {:display-name "ImTablesRootErrorBoundary"
      :get-derived-state-from-error (fn [_]
                                      (reset! caught? true)
                                      #js {})
      :component-did-catch (fn [_ error info]
                             (reset! !error {:type :boundary
                                             :error error
                                             :info info}))
      :render (fn [this]
                (let [{:keys [location]} (reagent/props this)
                      clear-error! (fn []
                                     (dispatch-sync [:im-tables/restart location])
                                     (reset! !error nil)
                                     (reset! caught? false))]
                  (if @caught?
                    (when-let [error @!error]
                      [:div.im-table.relative
                       [error/failure location error
                        :clear-error! clear-error!]])
                    (into [:<>] (reagent/children this)))))})))

;; The static preview and the first click [im-7os.40]
;;
;; Until the user shows interest in a table, `main` renders a cheap static HTML copy of it
;; (the dashboard as static markup and a plain <table>) instead of the real components. That
;; keeps a page with many tables (or a big result page) fast to paint, because the live
;; table (every cell, the toolbars with their dropdowns and subscriptions) is only built for
;; the table the user actually touches. The first mouseover switches to the live version.
;;
;; The static buttons and icons have no handlers, so a click that lands before the live
;; version has replaced them (a fast click, a touch screen where there is no hover before
;; the tap, a keyboard user) used to be lost: the first click on Export, a column summary or
;; a filter did nothing. We still switch on mouseover, and now also on pointerdown and on
;; keyboard focus, but a switch that begins with a press cannot simply stop there: the
;; element that was pressed is thrown away by the re-render, so the browser never delivers
;; the click. So the pointer handlers remember where the press happened, wait until the live
;; markup is in the DOM, and then `.click()` the live element at that position, provided it
;; is the same kind of element that was pressed (never a different control that happens to
;; sit there). Focus is moved the same way for keyboard users.
;;
;; The trade-off: on a touch screen a scroll gesture that starts on the table now builds
;; the live table, which it did not before; and the forwarded click is one frame or two
;; later than a native one. Both are small next to the cost of a dead first click, and the
;; lazy rendering stays for every table the pointer never reaches.

(def ^:private static-class "im-table-static")

(defn- static-markup-present?
  "True while the static preview is in the DOM below `root`."
  [root]
  (boolean (some-> root (.querySelector (str "." static-class)))))

(defn- element-at [[x y]]
  (.elementFromPoint js/document x y))

(defn- await-live-markup
  "Calls `then` once the static preview has been replaced by the live table in the
  DOM of `root`. Reagent re-renders on the next animation frame, so poll the frames
  (for at most about a second, then give up silently)."
  [root then]
  (letfn [(poll [frames-left]
            (if (and (static-markup-present? @root) (pos? frames-left))
              (js/requestAnimationFrame #(poll (dec frames-left)))
              (then)))]
    (js/requestAnimationFrame #(poll 60))))

(defn- same-kind? [a b]
  (and (some? a) (some? b) (= (.-tagName a) (.-tagName b))))

(defn- static-forwarding
  "Returns {:on-pointer-down f :on-focus f} for the root element of a table. They
  flip `static?` and forward the interaction to the live table (see above). `!root`
  is an atom holding the root DOM element."
  [static? !root]
  (let [!pending (atom nil)
        ;; Forwards the click once the live markup is there AND the pointer was released.
        maybe-forward! (fn []
                         (let [{:keys [down up live? tag]} @!pending]
                           (when (and live? up)
                             (reset! !pending nil)
                             (let [el (element-at down)]
                               (when (and el
                                          (= el (element-at up))
                                          (= tag (.-tagName el))
                                          (.contains @!root el))
                                 (.click el)
                                 ;; A synthetic click does not focus form controls.
                                 (when (.matches el "input, select, textarea")
                                   (.focus el)))))))
        on-pointer-up (fn [e]
                        (swap! !pending #(when % (assoc % :up [(.-clientX e) (.-clientY e)])))
                        (maybe-forward!))
        on-pointer-cancel (fn [] (reset! !pending nil))]
    {:on-pointer-down
     (fn [e]
       (when (and (.-isPrimary e)
                  (zero? (.-button e))
                  (static-markup-present? @!root))
         (reset! !pending {:down [(.-clientX e) (.-clientY e)]
                           :tag (.-tagName (.-target e))})
         (reset! static? false)
         (.addEventListener js/document "pointerup" on-pointer-up #js {:once true})
         (.addEventListener js/document "pointercancel" on-pointer-cancel #js {:once true})
         (await-live-markup !root (fn []
                                    (swap! !pending #(when % (assoc % :live? true)))
                                    (maybe-forward!)))))
     :on-focus
     (fn [e]
       ;; Tab onto a static button: switch, then focus the same button in the live table.
       ;; (A press that focuses the button is handled by :on-pointer-down above.)
       (when (and (nil? @!pending) (static-markup-present? @!root))
         (let [target (.-target e)
               rect (.getBoundingClientRect target)
               point [(+ (.-left rect) (/ (.-width rect) 2))
                      (+ (.-top rect) (/ (.-height rect) 2))]]
           (reset! static? false)
           (await-live-markup !root (fn []
                                      (let [el (element-at point)]
                                        (when (and (same-kind? el target)
                                                   (.contains @!root el))
                                          (.focus el))))))))}))

(defn main [location]
  (let [response (subscribe [:main/query-response location])
        pagination (subscribe [:settings/pagination location])
        modal-markup (subscribe [:modal location])
        static? (reagent/atom true)
        !root (atom nil)
        forwarding (static-forwarding static? !root)
        model (subscribe [:assets/model location])
        query (subscribe [:main/query location])
        collapsed-views (subscribe [:query-response/views-collapsed-by-joins location])]
    (fn [location]
       ; The query results are stored in a map with the result index as the key.
       ; In other words, we're not using a vector! To generate a speedy preview,
       ; get the first n results to be shown as a simple table:
       ; (get results 0), (get results 1), (get results 2) ... (get results limit)
      (let [preview-rows (map (partial get (:results @response)) (range (:limit @pagination)))]
        [error-boundary {:location location}
         [:div.im-table.relative
           ; When the mouse touches the table (or a pointer is pressed on it, or the keyboard
           ; focuses it), set the flag to render the actual React components. See the comment
           ; on the static preview above for how the first click or focus is forwarded.
          (merge {:ref (fn [el] (reset! !root el))
                  :on-mouse-over (fn []
                                   (when @static?
                                     (reset! static? false)))}
                 forwarding)

          (if @static?
             ; If static (optimised) then only show an HTML representations of the React components
            [:div {:class static-class}
              ; Force the dashboard buttons to be just HTML (their lights are on but no one is home)
             [:div {:dangerouslySetInnerHTML (unsafe-html (server/render-to-static-markup [dashboard/main location @response @pagination]))}]
             [table/handle-states location @response
              [:div.table-container
               [:table.table.table-condensed.table-bordered.table-striped
                ; Good old static (fast) html table:
                [:thead
                 (into [:tr]
                       (->> @collapsed-views
                            (map-indexed (fn [idx h]
                                           (let [[attrib-name parent-name] (when (and @model h)
                                                                             (rseq (impath/display-name @model h)))
                                                 active-filters (not-empty (filter (partial constraint-has-path? h) (:where @query)))]
                                             ; This is a simple HTML representation of
                                             ; im-tables.views.table.head.controls/toolbar
                                             ; If you modify this form or the one in the toolbar, please remember to modify both
                                             ; or they won't look the same when the table is activated
                                             [:th
                                              [:div.summary-toolbar
                                               [:i.fa.fa-sort.sort-icon]
                                               [:i.fa.fa-times.remove-icon]
                                               [:i.fa.fa-filter.dropdown-toggle.filter-icon
                                                {:class (when active-filters "active-filter")}]
                                               [:i.fa.fa-bar-chart.dropdown-toggle {:data-toggle "dropdown"}]]
                                              [:div
                                               [:div parent-name]
                                               [:div attrib-name]]])))))]
                (into [:tbody] (map (fn [row]
                                      (into [:tr]
                                            (map (fn [{:keys [value]}]
                                                   [:td
                                                    (if (some? value)
                                                      [:a (str value)]
                                                      [:a.no-value "NO VALUE"])])
                                                 row)))
                                    preview-rows))]]]]
             ; Otherwise show the interactive React components
            [:div
             [dashboard/main location @response @pagination]
             [table/main location @response @pagination]])

           ; Only show the modal when the modal subscription has a value
          (when @modal-markup [custom-modal location @modal-markup])]]))))
