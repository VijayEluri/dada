(ns 
 #^{:author "Jules Gosnell" :doc "Demo domain for DADA"}
 org.dada.demo.whales
 (:use [org.dada.core])
 (:import [org.dada.core Getter GetterMetadata VersionedModelView])
 )

;; TODO - this will have to wait until we have an extended ClassFactory
;; (do
;;   (def Cetacea (make-class factory "org.dada.whales.Cetacea" Object [(Float/TYPE) "length"] [(Float/TYPE) "weight"]))
;;   (def Mysticeti (make-class factory "org.dada.whales.Mysticeti" Cetacea))
;;   (def Balaenopteridae (make-class factory "org.dada.whales.Balaenopteridae" Mysticeti))
;;   (def Eschrichtiidae (make-class factory "org.dada.whales.Eschrichtiidae" Mysticeti))
;;   (def Neobalaenidae (make-class factory "org.dada.whales.Neobalaenidae" Mysticeti))
;;   (def Balaenidae (make-class factory "org.dada.whales.Balaenidae" Mysticeti))
;;   (def Odontoceti (make-class factory "org.dada.whales.Odontoceti" Cetacea))
;;   (def Physeteroidea (make-class factory "org.dada.whales.Physeteroidea" Odontoceti))
;;   (def Zyphiidae (make-class factory "org.dada.whales.Zyphiidae" Odontoceti))
;;   (def Delphinidae (make-class factory "org.dada.whales.Delphinidae" Odontoceti))
;;   )


;; mysticeti				; Baleen Whales
;;  balaenidae                          ; Right, Bowhead Whales
;;  balaenopteridae                     ; Rorqual Whales-Blue, Minke, Bryde's, Eden, Humpback
;;  eschrichtiidae                      ; Gray Whales
;;  neobalaenidae                       ; Pygmy Right Wale
;; odontoceti				; Toothed Whales
;;  physeteroidea			; Sperm Whales
;;  zyphiidae                           : Beaked Whales
;;  delphinidae                         ; Orca, Dolphins


(def types '(
	     "blue whale"
	     "sei whale"
	     "bottlenose dolphin"
	     "killer whale"
	     "false killer whale"
	     "amazon river dolphin"
	     "narwhal"
	     "sperm whale"
	     "pilot whale"
	     "beluga whale"
	     "humpback whale"
	     "fin whale"
	     "right whale"
	     "bowhead whale"
	     "brydes whale"
	     "minke whale"
	     "gray whale"
	     "spinner dolphin"
	     "melon headed whale"
	     "beaked whale"
	     ))

(defn rnd [seq] (nth seq (rand-int (count seq))))

;; IDEAS

;; spotting - whale-id, time, coordinates, weight, length
;; birth - whale-id time, weight, length, location
;; death - id, time, weight, length


;;(def factory (new ClassFactory))

;; TODO - factory comes from core

(def properties (list [(Long/TYPE) "time"]
		      [(Integer/TYPE) "version"]
		      [String "type"]
		      [(Float/TYPE) "length"]
		      [(Float/TYPE) "weight"]))

(def Whale (apply make-class "org.dada.demo.whales.Whale" Object properties))

(def *metamodel* (start-server "Cetacea"))
(start-client "Cetacea")

(let
    [types (map (fn [property] (nth property 0)) properties)
     names (map (fn [property] (nth property 1)) properties)
     getters (map (fn [property] (make-proxy-getter Whale (nth property 0) (nth property 1))) properties)
     key-getter (nth getters 0)
     version-getter (nth getters 1)
     metadata (new GetterMetadata Whale types names getters)
     model-name "Whales"]
  (def model (new VersionedModelView model-name metadata key-getter version-getter))
  (insert *metamodel* model)
  model)

;; create 10,000 whales and insert them into model
(insert-n
 model
 (let [time (System/currentTimeMillis)]
   (for [id (range time (+ time 1000))] ;;10,000
     (make-instance org.dada.demo.whales.Whale 
		    id
		    0
		    (rnd types)
		    (/ (rand-int 3290) 100) ;; 32.9 metres
		    (/ (rand-int 172) 100))))) ;; 172 metric tons

;; syntactic sugar
(defn fetch [& args]
  (let [output (apply select args)]
    (insert *metamodel* output)
    output))

;; select whales into a new model...
(def narwhals (fetch model "time" "version" '(["time"] ["version"] ["type"]["weight"]) :model "Narwhals" :filter (fn [value] (= "narwhal" (. value getType)))))

(def belugas (fetch model "time" "version" '(["time"] ["version"] ["type"]["length"]) :model "Belugas" :filter (fn [value] (= "beluga whale" (. value getType)))))

(import org.dada.core.AggregatedModelView)
(import org.dada.core.AggregatedModelView$Aggregator)

;; make a class to hold key, version and aggregate value...

(def Length-properties 
     (list
      [String "type"]
      [(Integer/TYPE) "version"]
      [(Float/TYPE) "length"]))

(def Length
     (apply
      make-class
      "org.dada.demo.whales.Length" Object Length-properties))

(def sum
     (AggregatedModelView.
      "Beluga.Length"
      (new GetterMetadata
	   Length
	   (map (fn [property] (.getCanonicalName (first property))) Length-properties)
	   (map second Length-properties)
	   (map 
	    (fn [property]
		(make-proxy-getter 
		 Length
		 (first property)
		 (second property)))
	    Length-properties))
      "beluga whale"
      (proxy
       [AggregatedModelView$Aggregator]
       []
       (initialValue []
		     (new Float "0"))
       (currentValue [key version value]
		     (make-instance Length key version value))
       (aggregate [insertions updates deletions]
		  (- 
		   (+
		    (apply + (map #(.getLength (.getNewValue %)) insertions))
		    (apply + (map #(.getLength (.getNewValue %)) updates))
		    )
		   (+
		    (apply + (map #(.getLength (.getOldValue %)) updates))
		    (apply + (map #(.getLength (.getOldValue %)) deletions))
		    )))
       (apply [currentValue delta]
	      (+ currentValue delta))
       )
      ))

(insert *metamodel* sum)

(connect belugas sum)