(ns org.dada2.web.table-view
  (import
   [com.vaadin.ui UI Label Table]
   [com.vaadin.data Item Property Property$ValueChangeListener]
   [com.vaadin.event ItemClickEvent ItemClickEvent$ItemClickListener])
  (use
   [org.dada2 core])
  )

(defn- upsert [^Table table upsertion pk-fn vals-fn]
  (let [pk (pk-fn upsertion)]
    (if-let [^Item item (.getItem table pk)]
      (doseq [[id new-value] (map list (.getItemPropertyIds item) (vals-fn upsertion))]
        (let [^Property property (.getItemProperty item id)
              old-value (.getValue property)]
          (if (not (= old-value new-value))
            (.setValue property new-value))))
      (.addItem table (into-array Object (vals-fn upsertion)) pk))))

(deftype TableView [^UI ui ^Table table pk-fn vals-fn]
  View
  (on-upsert [this upsertion]
    (.access ui (fn [] (upsert table upsertion pk-fn vals-fn)))
    this)
  (on-upserts [this upsertions] 
    (.access ui (fn [] (doseq [upsertion upsertions] (upsert table upsertion pk-fn vals-fn))))
    this)
  Object
  (^String toString [this] (.toString table))
  )

;; metadata - list of tuples: [key type]
(defn table-view [^UI ui ^Table table pk-fn metadata]
  (.setNullSelectionAllowed table false)
  (.addListener table
                (reify ItemClickEvent$ItemClickListener
                  (^void itemClick [_ ^ItemClickEvent event] 
                    (if (.isDoubleClick event)
                      (println "DOUBLE CLICK: " (.getValue table)))
                    nil)))
  (doseq [[key type] metadata] (.addContainerProperty table (.toString key) type nil))
  (TableView. ui table pk-fn vals))

;; support for deleting rows
;; flash cells on update
;; is this the best way to get what we want :?
;; why are all column headers in all-caps

