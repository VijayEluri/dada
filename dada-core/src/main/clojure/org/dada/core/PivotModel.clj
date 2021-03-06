(ns org.dada.core.PivotModel
    (:import
     [java.util Collection LinkedHashMap Map]
     [org.dada.core AbstractModel Attribute Data Getter Metadata RemoteModel Tuple Update]
     )
    (:gen-class
     :extends org.dada.core.AbstractModelView
     :implements [ java.io.Serializable]
     :constructors {[String org.dada.core.Metadata java.util.Collection Object java.util.Collection org.dada.core.Metadata] [String org.dada.core.Metadata]}
     :methods [[writeReplace [] Object]]
     :init init
     :state state
     )
    )

;; TODO: consider supporting indexing on mutable keys - probably not a good idea ?

;; create a map of pivot-key : list of (fn [new-value old-values] ...) where e.g.
;; (apply tgt-creator (map #(% new-value old-values) (pivot-map key)))
;; will copy the value into a new version of the correctly keyed pivotted row...

;; TODO: at the moment a new pivotted row is produced for every
;; underlying data change. Perhaps it would be more efficient to sort
;; data changes into rows and then only create each row once ? Or
;; would we just be moving the weight of object creation from pivotted
;; row to some other more complex form ?

(defn make-pivot-map [pivot-keys #^Metadata tgt-metadata]
  ;;(println "PIVOT KEYS" pivot-keys)
  (let [version-getter (.getVersionGetter tgt-metadata)
	;; a map of keys and fns taking new and old values and
	;; returning the application of the getter on the old value
	#^Map getter-map (apply
			  array-map
			  (mapcat
			   (fn [#^Attribute new]
			       [(.getKey new)
				(let [#^Getter getter (.getGetter new)]
				  (fn [new-value old-values] (.get getter old-values)))])
			   (.getAttributes tgt-metadata)))

	;; #^Map getter-map (reduce
	;; 		   (fn [old #^Attribute new]
	;; 		       (assoc old (.getKey new) (let [#^Getter getter (.getGetter new)]
	;; 						  (fn [new-value old-values] (.get getter old-values)))))
	;; 		   (array-map)
	;; 		   (reverse (.getAttributes tgt-metadata)))
	;; items are pushed on head of map, so we reverse before starting

	;; attribute-keys (map #(.getKey %) attributes)
	;; attribute-getters (map #(.getGetter %) attributes)
	;; #^Map getter-map (apply
	;; 		  array-map
	;; 		  (interleave attribute-keys
	;; 			      (map #(fn [new-value old-values] (.get #^Getter % old-values)) attribute-getters)))
	]

    (apply
     hash-map
     (apply
      concat     
      (map
       (fn [pivot-key]
	   (let [#^Map pivot-map (LinkedHashMap. getter-map)]
	     (.put pivot-map pivot-key (fn [new-value old-values] new-value))
	     ;; TODO: increment version...properly...
	     ;; TODO: why are we using a mutable Map here ?
	     (.put pivot-map :version (fn [new-value old-values] (let [version (.get version-getter old-values)] (if version (+ version 1) 0))))
	     [pivot-key (vals pivot-map)]))
       pivot-keys)))
    ))

(defn -init [#^String model-name
	     #^Metadata src-metadata  ;e.g. [day count]
	     #^Collection const-keys  ; e.g. ["orca" "atlantic"]
	     #^Comparable src-value-key  ;e.g. :count
	     #^Collection tgt-keys    ;e.g. [:mon :tue :wed :thu :fri]
  	     #^Metadata tgt-metadata] ;e.g. [type mon tue wed thu fri]
  ;;(println "PIVOT:" model-name src-metadata const-keys src-value-key tgt-keys tgt-metadata)
  [ ;; super ctor args
   [model-name tgt-metadata]
   ;; instance staten
   (let [key-getter (.getPrimaryGetter src-metadata)
	 key-fn (fn [value] (.get key-getter value))
	 version-comparator (.getVersionComparator src-metadata)

	 ;;----------------------------------------
	 ;; pivot stuff

	 pivot-creator (.getCreator tgt-metadata)
	 pivot-initial-value (.create 
			      pivot-creator
			      (into-array
			       Object
			       (concat
				const-keys
				(take (- (.size (.getAttributes tgt-metadata)) (count const-keys)) (repeat nil)))))
	 src-value-getter (.getGetter (.getAttribute src-metadata src-value-key))
	 pivot-map (make-pivot-map tgt-keys tgt-metadata)

	 pivot-fn (fn [old-pivotted key src-value] ;e.g. [["Sei Whale" Mon ... 20 ...] Mon [Mon 21]]
		      (let [src-value-value (.get src-value-getter src-value) ;; e.g. 20
			    pivot-fns (pivot-map key)
			    ;;dummy (println "PIVOT FNS" pivot-fns)
			    pivot-values (map #(% src-value-value old-pivotted) pivot-fns)
			    ;;dummy (println "PIVOT VALUES" pivot-values)
			    new-pivotted (.create pivot-creator (into-array Object pivot-values))]
			new-pivotted))

	 unpivot-fn (fn [old-pivotted key]
			;; TODO
			old-pivotted)			

	 ;;----------------------------------------

	 process-addition
	 (fn [[extant extinct pivotted i a d] #^Update addition]
	     ;;(println "PIVOT - PROCESS-ADDITION" addition)
	     (let [new (.getNewValue addition)
		   ;;dummy (println "PIVOT - PROCESS-ADDITION" new)
		   key (key-fn new)
		   ;;dummy (println "PIVOT - PROCESS-ADDITION" key)
		   key (if (instance? Tuple key) (last key) key) ;TODO - last is a hack - pivot-map keyed differently from data
		   ;;dummy (println "PIVOT - PROCESS-ADDITION" key)
		   current (extant key)]
	       ;;(println "PIVOT - PROCESS-ADDITION" current)
	       (if (nil? current)
		 ;; insertion...
		 (let [removed (extinct key)]
		   (if (nil? removed)
		     ;; first time seen
		     (let [new-pivotted (pivot-fn pivotted key new)]
		       [(assoc extant key new) extinct new-pivotted (cons (Update. nil new-pivotted) i) a d]) ;insertion
		     ;; already deleted
		     (if (< (.compareTo version-comparator removed new) 0)
		       ;; later version - reinstated
		       (let [new-pivotted (pivot-fn pivotted key new)]
			 [(assoc extant key new) (dissoc extinct key) new-pivotted (cons (Update. nil new-pivotted) i) a d])
		       (do
			 ;; out of order or duplicate version - ignored
			 ;;(println "WARN: OUT OF ORDER INSERT" current new)
			 [extant extinct pivotted i a d]))
		     )
		   )
		 ;; alteration...
		 (if (< (.compareTo version-comparator current new) 0)
		   ;; later version - accepted
		   (let [new-pivotted (pivot-fn pivotted key new)]
		     [(assoc extant key new) extinct new-pivotted i (cons (Update. pivotted new-pivotted) a) d]) ;alteration
		   (do
		     ;; out of order or duplicate version - ignored
		     ;;(println "WARN: OUT OF ORDER UPDATE" current new)
		     [extant extinct pivotted i a d]))
		 )))

	 process-deletion
	 (fn [[extant extinct pivotted i a d] #^Update deletion]
	     (let [new (.getNewValue deletion)
		   key (last (key-fn new))
		   current (extant key)]
	       (if (nil? current)
		 (let [removed (extinct key)]
		   (if (nil? removed)
		     ;; neither extant or extinct - mark extinct
		     [extant (dissoc extinct key) pivotted i a d]
		     (if (< (.compareTo version-comparator removed new) 0)
		       ;; later version - accepted
		       (let [new-pivotted  (pivot-fn pivotted key new)]
			 [extant (assoc extinct key new) new-pivotted i a (cons (Update. pivotted new-pivotted) d)]) ;TODO - is this right ?
		       (do
			 ;; earlier version - ignored
			 ;;(println "WARN: OUT OF ORDER DELETION" current new)
			 [extant extinct pivotted i a d]))))
		 (if (< (.compareTo version-comparator current new) 0)
		   ;; later version - accepted
		   (let [new-pivotted (unpivot-fn pivotted key)]
		     [(dissoc extant key) (assoc extinct key new) new-pivotted i a (cons (Update. pivotted new-pivotted) d)])
		   (do
		     ;; earlier version - ignored
		     ;;(println "WARN: OUT OF ORDER DELETION" current new)
		     [extant extinct pivotted i a d])))))

	 ;; TODO: perhaps we should raise the granularity at which we
	 ;; compare-and-swap, in order to avoid starvation of larger
	 ;; batches...
	 swap-state-fn (fn [[extant extinct pivotted] insertions alterations deletions]
			   (reduce process-deletion
				   (reduce process-addition
					   (reduce process-addition
						   [extant extinct pivotted '() '() '()]
						   insertions)
					   alterations)
				   deletions))

	 mutable-state (atom [{}{} pivot-initial-value])

	 update-fn
	 (fn [inputs]
	     (let [[_ _ _ i a d] (apply swap! mutable-state swap-state-fn inputs)]
	       [i a d]))
	 
	 getData-fn
	 (fn []
	     (let [[_ _ pivotted] @mutable-state]
	       (Data. [pivotted] nil)))
	 ]
     
     [update-fn getData-fn model-name tgt-metadata])
   ])

(defn -getData [#^org.dada.core.PivotModel this]
  (let [[_ getData-fn] (.state this)]
    (getData-fn)))

(defn -update [#^org.dada.core.PivotModel this & inputs]
  (let [[update-fn] (.state this)
	[#^Collection i #^Collection a #^Collection d] (update-fn inputs)]
    (if (not (and (empty? i) (empty? a) (empty? d)))
      (.notifyUpdate this i a d))))

;;--------------------------------------------------------------------------------

(defn #^{:private true} -writeReplace [#^org.dada.core.PivotModel this]
  (let [[_ _ name metadata] (.state this)]
      (RemoteModel. name metadata)))
