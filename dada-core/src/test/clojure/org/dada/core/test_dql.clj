(ns org.dada.core.test-dql
    (:use 
     [clojure test]
     [org.dada core]
     [org.dada.core dql])
    )

(def whale-data
     ;;[id, version, type, ocean, length]
     [[0 0 :blue :atlantic 100]
      [1 0 :blue :pacific  100]
      [2 0 :grey :atlantic 50]
      [3 0 :grey :pacific  50]])

(def whales (model "Whales" (seq-metadata (count (first whale-data)))))
(insert *metamodel* whales)
(insert-n whales whale-data)

;;--------------------------------------------------------------------------------
;; THOUGHTS:
;; we should be able to test serially and concurrently (using exclusive lock)
;;--------------------------------------------------------------------------------
;; utils

(defn reduction-value [[metadata-fn data-fn]]
  (let [[metamodel] (data-fn)
	model (first (first (.getExtant (.getData metamodel))))
	value (first (.getExtant (.getData model)))]
    (.get (.getGetter (nth (.getAttributes (.getMetadata model)) 1)) value)))

(defn flat-split-values [[metadata-fn data-fn]]
  (let [[metamodel] (data-fn)
	models (.getExtant (.getData metamodel))]
    (reduce (fn [result [model path]] (conj result [(map second path) (.getExtant (.getData model))])) {} models)))

(defn nested-split-values [[metadata-fn data-fn]]
  (let [[metamodel] (data-fn)
	models (.getExtant (.getData metamodel))]
    (reduce (fn [result [model path]] (conj result [(map second path) (.getExtant (.getData model))])) {} models)))

;;--------------------------------------------------------------------------------

(deftest test-dcount
  (is (= (reduction-value (? (dcount)(dfrom "Whales")))
	 (count whale-data))))

(deftest test-dsum
  (is (= (reduction-value (? (dsum 4)(dfrom "Whales")))
	 (reduce (fn [total whale] (+ total (nth whale 4))) 0 whale-data))))

;; one dimension -  {[[key][whale...]]...}
(deftest test-split-1d
  (is (= (flat-split-values (? (dsplit 2)(dfrom "Whales")))
	 (group-by (juxt (fn [[id version type]] type)) whale-data))))

;; 2 dimensions - flat - {[[key1 key2][whale...]]...}
(deftest test-flat-split-2d
  (is (= (flat-split-values (? (dsplit 3)(dsplit 2)(dfrom "Whales")))
	 (group-by (juxt (fn [[_ _ type]] type)(fn [[_ _ _ ocean]] ocean)) whale-data))))

;; 2 dimensions - nested - a map of {[[key1]{[[key2][whale...]]}]...}
;; (deftest test-nested-split-2d
;;   (is (= (nested-split-values (? (dsplit 2 list [(dsplit 3)])(dfrom "Whales")))
;;  	 (reduce
;; 	  (fn [result [key vals]] (conj result [key (group-by (juxt (fn [[_ _ _ ocean]] ocean)) vals)]))
;; 	  {}
;; 	  (group-by (juxt (fn [[_ _ type]] type)) whale-data)))))

