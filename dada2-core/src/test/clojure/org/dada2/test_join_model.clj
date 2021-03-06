(ns org.dada2.test-join-model
    (:use
     [clojure test]
     [clojure.tools logging]
     [org.dada2 utils]
     [org.dada2 core]
     [org.dada2 test-core]
     [org.dada2 map-model]
     [org.dada2 split-model]
     ;;[org.dada2 join-model]
     )
    (:import
     [clojure.lang Atom]
     [org.dada2.core ModelView])
    )
;;--------------------------------------------------------------------------------
;; 1x left-hand-side joined to Nx right-hand-sides via common key fns
;; state lives in join model
;; 1x joined model contains the result of calling the join-fn on 1x lhs and Nx rhs for each successful join
;; 1+N unjoined models, containing any unjoined members of lhs and rhs sources
;; as row becomes involved in a join it is deleted from its unjoined model and upserted into the joined model

;;--------------------------------------------------------------------------------
;; this layer encapsulates access to lhs index :
;;  [{lhs-pk = lhs}{fk = {lhs-pk = lhs}}... ]

(defn- lhs-init [n]
  "return initial representation for lhs-index"
  (into [] (repeat (inc n) {})))

(defn- lhs-get [[h & _] lhs-pk]
  "return current lhs for pk"
  (h lhs-pk))

(defn- lhses-get [[_ & lhs-indeces] i fk]
  "return seq of lhses indexed by i and fk"
  (vals ((nth lhs-indeces i) fk)))

(defn- lhs-assoc [[h & lhs-indeces] lhs-pk-key lhs-fk-keys lhs]
  "associate a lhs with index"
  (let [lhs-pk (lhs-pk-key lhs)]
    (apply
     vector
     (assoc h lhs-pk lhs)
     (map
      (fn [lhs-index lhs-fk-key] (assoc-in lhs-index [(lhs-fk-key lhs) lhs-pk] lhs)) ;TODO - expand
      lhs-indeces
      lhs-fk-keys))))

(defn- lhs-dissoc [[h & lhs-indeces] lhs-pk-key lhs-fk-keys lhs]
  "dissociate an lhs from index"
  (let [lhs-pk (lhs-pk-key lhs)]
    (apply
     vector
     (dissoc h lhs-pk)
     (map
      (fn [lhs-index lhs-fk-key]
        (let [lhs-fk (lhs-fk-key lhs)
              tmp (dissoc (lhs-index lhs-fk) lhs-pk)]
          (if (empty? tmp) (dissoc lhs-index lhs-fk)(assoc lhs-index lhs-fk tmp))))
      lhs-indeces
      lhs-fk-keys))))

(deftest test-lhs-access
  (let [before (lhs-init 1)
	after  [{3 "xxx"} {String {3 "xxx"}}]]
    (is (= [{}{}] before))
    (is (= after (lhs-assoc before count [type] "xxx")))
    (is (= "xxx" (lhs-get after 3)))
    (is (= '("xxx") (lhses-get after 0 String)))
    (is (= [{}{}] (lhs-dissoc after count [type] "xxx")))
    ))

;;--------------------------------------------------------------------------------
;; this layer encapsulates access to rhs indeces :
;;  [[{rhs-pk = rhs}{fk = {rhs-pk = rhs}}]... ]
 
(defn- rhs-init [rhs-count]
  "return initial representation for rhs-indeces"
  (into [] (repeat rhs-count [{}{}])))

(defn- rhs-get [old-rhs-indeces i pk]
  "return current rhs by i and pk"
  ((first (nth old-rhs-indeces i)) pk))

(defn- rhses-get [rhs-indeces lhs-fk-keys lhs]
  "return a lazy seq of seqs of rhses for a given seq of fk-key functions"
  (map (fn [[_ rhs-index] lhs-fk-key] (vals 
                                       ;; TODO - following get should be lookup-fn
                                       (get rhs-index (lhs-fk-key lhs))
                                       )) rhs-indeces lhs-fk-keys))

(defn- rhs-assoc [rhs-indeces i rhs-pk-key fk rhs]
  "associate a new rhs with indeces"
  (let [[forward rhs-index] (nth rhs-indeces i)
        rhs-pk (rhs-pk-key rhs)]
    (assoc rhs-indeces i
           [(assoc forward rhs-pk rhs)
            (assoc-in rhs-index [fk rhs-pk] rhs)]))) ;TODO - expand

(defn- rhs-dissoc [rhs-indeces i rhs-pk-key fk rhs]
  "dissociate an rhs from indeces"
  (let [rhs-pk (rhs-pk-key rhs)
        [forward rhs-index] (rhs-indeces i)
        tmp (dissoc (rhs-index fk) rhs-pk)]
    (assoc rhs-indeces i
           [(dissoc forward rhs-pk)
            (if (empty? tmp)
              (dissoc rhs-index fk)
              (assoc rhs-index fk tmp))])))

(deftest test-rhs-access
  (let [before (rhs-init 1)
	after  [[{3 "xxx"}{String {3 "xxx"}}]]]
    (is (= before [[{}{}]]))
    (is (= after (rhs-assoc before 0 count String "xxx")))
    (is (= "xxx" (rhs-get after 0 3)))
    (is (= '(("xxx")) (rhses-get after [first] [String])))
    (is (= [[{}{}]] (rhs-dissoc after 0 count String "xxx")))))

;;--------------------------------------------------------------------------------
;;; calculate all permutations for a given list of sets...
;;; e.g. 
;;; (permute [[]] [[:a1 :a2][:b1 :b2 :b3][:c1]]) ->
;;; ([:a1 :b1 :c1] [:a1 :b2 :c1] [:a1 :b3 :c1] [:a2 :b1 :c1] [:a2 :b2 :c1] [:a2 :b3 :c1])
;;; TODO:
;;;  should use recurse
;;;  should support both inner and outer joins
(defn- permute [state [head & tail]]
  (if (empty? head)
    state
    (permute (mapcat (fn [i] (map (fn [j] (conj i j)) head)) state) tail)))

(deftest test-permute
  (is (= 
       [[:a1 :b1 :c1] [:a1 :b2 :c1] [:a1 :b3 :c1] [:a2 :b1 :c1] [:a2 :b2 :c1] [:a2 :b3 :c1]]
       (permute [[]] [[:a1 :a2][:b1 :b2 :b3][:c1]]))))

;;--------------------------------------------------------------------------------
;; TODO: split the calculation of the joins and their transposition...

(defn- transpose [lhs join-fn permutations]
  (map (fn [permutation] (apply join-fn lhs permutation)) permutations))

(defn- lhs-join [rhs-indeces lhs-fk-keys lhs join-fn]
  "calculate the set of joins for a newly arriving lhs"
  (let [permutations (permute [[]] (rhses-get rhs-indeces lhs-fk-keys lhs))]
        (if (= [[]] permutations)
          nil
          (transpose lhs join-fn permutations))))

(defn- rhs-join [rhs-indeces lhs-fk-keys join-fn lhs-indeces i fk]
  "calculate the set of joins for a newly arriving rhs"
  (mapcat (fn [lhs] (lhs-join rhs-indeces lhs-fk-keys lhs join-fn)) (lhses-get lhs-indeces i fk)))

;;--------------------------------------------------------------------------------
;; this layer encapsulates access to joins
;; {pk={join-pk=join}} 

(defn- join-init [n]
  (into [] (repeat (inc n) {})))

(defn- dissoc-join [old-joins join-pk]
  (mapv
   (fn [key join-map]
     (let [n (dissoc (key join-map) join-pk)]
       (if (empty? n)
         (dissoc join-map key)
         (assoc join-map key n))))
   join-pk
   old-joins))

(defn- assoc-join [old-joins join-pk join]
  (mapv
   (fn [key join-map]
     (assoc join-map key
            (assoc (key join-map) join-pk join)))
   join-pk
   old-joins))

(defn- get-lhs-join-map [[lhs-joins & _] key]
  "return a map (join-pk:join} of all joins in which this lhs is involved."
  (lhs-joins key))

(defn- get-rhs-join-map [[_ & rhs-joins] i key]
  "return a map (join-pk:join} of all joins in which this rhs is involved."
  ((nth rhs-joins i) key))

(deftest test-manage-joins
  (let [j1 (join-init 2)
        j2 (assoc-join j1 [:a :b :c] "abc")
        a (get-lhs-join-map j2 :a)
        b (get-rhs-join-map j2 0 :b)
        c (get-rhs-join-map j2 1 :c)
        j3 (dissoc-join j2 [:a :b :c])] 
    (is (= [{:a {[:a :b :c] "abc"}} {:b {[:a :b :c] "abc"}} {:c {[:a :b :c] "abc"}}] j2))
    (is (= {[:a :b :c] "abc"} a))
    (is (= {[:a :b :c] "abc"} b))
    (is (= {[:a :b :c] "abc"} c))
    (is (= [{} {} {}] j3))))

;;--------------------------------------------------------------------------------
;; use data from both left and right hand sides to create joins...

(defn- dissoc-joins [old-joins join-pk new-joins]
  "remove a seq of joins from a join index"
  (reduce (fn [joins join] (dissoc-join joins (join-pk join))) old-joins new-joins))

(defn- assoc-joins [old-joins join-pk new-joins]
  "add a seq of joins into a join index"
  (reduce (fn [joins join] (assoc-join joins (join-pk join) join))  old-joins new-joins))

;;--------------------------------------------------------------------------------

(defn- lhs-upsert [[old-lhs-index rhs-indeces old-joins] lhs-pk-key lhs-fk-keys lhs join-fn join-pk joined-model lhs-joined-model lhs-unjoined-model]
  (let [key (lhs-pk-key lhs)
        old-lhs (lhs-get old-lhs-index key)
        tmp-lhs-indeces (if old-lhs (lhs-dissoc old-lhs-index lhs-pk-key lhs-fk-keys old-lhs) old-lhs-index)
        new-lhs-indeces (lhs-assoc tmp-lhs-indeces lhs-pk-key lhs-fk-keys lhs)
        stale-joins (vals (get-lhs-join-map old-joins key))
	fresh-joins (lhs-join rhs-indeces lhs-fk-keys lhs join-fn)
        tmp-joins (if old-lhs (dissoc-joins old-joins join-pk stale-joins) old-joins)
	new-joins (assoc-joins tmp-joins join-pk fresh-joins)]
    [[new-lhs-indeces rhs-indeces new-joins]
     (fn [_]
       (on-deletes joined-model stale-joins)
       (on-upserts joined-model fresh-joins)
       )]))

(defn- lhs-delete [[old-lhs-indeces rhs-indeces old-joins] lhs-pk-key lhs-fk-keys lhs join-fn join-pk joined-model lhs-joined-model lhs-unjoined-model]
  (let [new-lhs-indeces (lhs-dissoc old-lhs-indeces lhs-pk-key lhs-fk-keys lhs)
        key (lhs-pk-key lhs)
        stale-joins (vals (get-lhs-join-map old-joins key))
	new-joins (dissoc-joins old-joins join-pk stale-joins)]
    [[new-lhs-indeces rhs-indeces new-joins]
     (fn [_]
       (on-deletes joined-model stale-joins))]))

;;; TODO
(defn- lhs-upserts [old-indeces i ks vs join-fn])
(defn- lhs-deletes [old-indeces i ks vs join-fn])

(defn- lhs-view [indeces lhs-pk-key lhs-fk-keys join-fn join-pk joined-model lhs-joined-model lhs-unjoined-model]
  (reify
   View
   ;; singleton changes
   (on-upsert [_ upsertion]
     ;;TODO - pass in views to notifier
     ((swap*! indeces lhs-upsert lhs-pk-key lhs-fk-keys upsertion join-fn join-pk joined-model lhs-joined-model lhs-unjoined-model) []) nil)
   (on-delete [_ deletion]
     ;;TODO - pass in views to notifier
     ((swap*! indeces lhs-delete  lhs-pk-key lhs-fk-keys deletion join-fn join-pk joined-model lhs-joined-model lhs-unjoined-model) []) nil)
   ;; batch changes - TODO
   (on-upserts [_ upsertions] (swap! indeces lhs-upserts lhs-fk-keys upsertions join-fn) nil)
   (on-deletes [_ deletions]  (swap! indeces lhs-deletes lhs-fk-keys deletions join-fn) nil)))

(defn- rhs-upsert [[lhs-indeces old-rhs-indeces old-joins] i rhs-pk-key rhs-fk-key lhs-fk-keys rhs join-fn join-pk joined-model rhs-joined-model rhs-unjoined-model]
  (let [pk (rhs-pk-key rhs)
        old-rhs (rhs-get old-rhs-indeces i pk)
        tmp-rhs-indeces (if old-rhs (rhs-dissoc old-rhs-indeces i rhs-pk-key (rhs-fk-key old-rhs) old-rhs) old-rhs-indeces)
        fk (rhs-fk-key rhs)
	new-rhs-indeces (rhs-assoc tmp-rhs-indeces i rhs-pk-key fk rhs)
        key (rhs-pk-key rhs)
        stale-joins (vals (get-rhs-join-map old-joins i key))
	fresh-joins (rhs-join new-rhs-indeces lhs-fk-keys join-fn lhs-indeces i fk)
	new-joins (assoc-joins (dissoc-joins old-joins join-pk stale-joins) join-pk fresh-joins)]
    [[lhs-indeces new-rhs-indeces new-joins]
     (fn [_]
       (on-deletes joined-model stale-joins)
       (on-upserts joined-model fresh-joins)
       
       ;; TODO: is there not a nicer way to do this ?
       (println "STALE: " stale-joins)
       (println "FRESH: " fresh-joins)
       ;; stale fresh joined unjoined
       ;;  t     t      -      u
       ;;  t     f      d      u
       ;;  f     t      u      d
       ;;  f     f      -      u
       (let [stale (not (empty? stale-joins))
             fresh (not (empty? fresh-joins))]
         (if (and stale (not fresh))
           (do
             (if rhs-joined-model (on-delete rhs-joined-model rhs))
             (if rhs-unjoined-model (on-upsert rhs-unjoined-model rhs)))
           (if (and (not stale) fresh)
             (do
               (if rhs-joined-model (on-upsert rhs-joined-model rhs))
               (if rhs-unjoined-model (on-delete rhs-unjoined-model rhs)))
             (if rhs-unjoined-model (on-upsert rhs-unjoined-model rhs))))
         ))]
    ))

;;; TODO - ugly, slow, ...
(defn- rhs-delete [[lhs-indeces old-rhs-indeces old-joins]
		   i rhs-pk-key rhs-fk-key lhs-fk-keys rhs join-fn join-pk joined-model rhs-joined-model rhs-unjoined-model]
  (let [fk (rhs-fk-key rhs)
	new-rhs-indeces (rhs-dissoc old-rhs-indeces i rhs-pk-key fk rhs)
        key (rhs-pk-key rhs)
        stale-joins (vals (get-rhs-join-map old-joins i key))
	new-joins (dissoc-joins old-joins join-pk stale-joins)]
    [[lhs-indeces new-rhs-indeces new-joins]
     (fn [_]
       (on-deletes joined-model stale-joins))]
    ))

;;; TODO
(defn- rhs-upserts [old-indeces i ks vs join-fn])
(defn- rhs-deletes [old-indeces i ks vs join-fn])

(defn- rhs-view [indeces i rhs-pk-key rhs-fk-key lhs-fk-keys join-fn join-pk joined-model rhs-joined-model rhs-unjoined-model]
  (reify
   View
   ;; singleton changes
   (on-upsert [_ upsertion]
     ((swap*! indeces rhs-upsert i rhs-pk-key rhs-fk-key lhs-fk-keys upsertion join-fn join-pk joined-model rhs-joined-model rhs-unjoined-model) []) nil) ;TODO: pass in views to notifier
   (on-delete [_ deletion]  ((swap*! indeces rhs-delete i rhs-pk-key rhs-fk-key lhs-fk-keys deletion  join-fn join-pk joined-model rhs-joined-model rhs-unjoined-model) []) nil) ;TODO: pass in views to notifier
   ;; batch changes
   (on-upserts [_ upsertions] (swap! indeces rhs-upserts i lhs-fk-keys upsertions join-fn) nil)
   (on-deletes [_ deletions]  (swap! indeces rhs-deletes i lhs-fk-keys deletions join-fn) nil)))

;;; attach lhs to all rhses such that any change to an rhs initiates an
;;; attempt to [re]join it to the lhs and vice versa.
(defn- join-views [join-fn join-pk joined-model [lhs-model lhs-pk-key lhs-fk-keys lhs-joined-model lhs-unjoined-model] & rhses]
  (let [rhs-count (count rhses)
        ;; need 2 pass initialisation
	indeces (atom [(lhs-init rhs-count) 
                       (mapv (fn [[_ _ _ state]] [{}(or state {})]) rhses) ;; (rhs-init rhs-count)
                       (join-init rhs-count)])]
    ;; view lhs
    (log2 :info (str " lhs: " lhs-model ", " lhs-fk-keys))
    (attach
     lhs-model
     (lhs-view indeces lhs-pk-key lhs-fk-keys join-fn join-pk joined-model lhs-joined-model lhs-unjoined-model))
    ;; view rhses
    (doall
     (map
      (fn [[rhs-model rhs-pk-key rhs-fk-key rhs-initial-state rhs-joined-model rhs-unjoined-model] i]
        (attach rhs-model (rhs-view indeces i rhs-pk-key rhs-fk-key lhs-fk-keys join-fn join-pk joined-model rhs-joined-model rhs-unjoined-model))
        )
      rhses
      (range)))
    indeces))

(deftype JoinModel [^String name ^Atom state ^Atom views]
  Model
  (attach [this view] (swap! views conj view) this)
  (detach [this view] (swap! views without view) this)
  (data [_] @state)
  Object
  (^String toString [this] name)
  )

(defn join-model [name joined-model joins join-fn join-pk]
  (let [state (apply join-views join-fn join-pk joined-model joins)]
    (->JoinModel name state (atom []))))

;;--------------------------------------------------------------------------------
;; tests

(defrecord A [name ^int version fk-b fk-c]
  Object
  (^String toString [_] (str name)))

(defrecord B [name ^int version b data]
  Object
  (^String toString [_] (str name)))

(defrecord C [name ^int version c data]
  Object
  (^String toString [_] (str name)))

(defrecord ABC [name version b-data c-data]
  Object
  (^String toString [_] (str name)))

;; an example of an aggressive join-fn
;; a "lazy" join fn would define the same interface, but hold references to the A,B and C...
;; a really clever impl might start lazy and become agressive during serialisation..
(defn- ^ABC join-abc [^A a ^B b ^C c]
  (->ABC
   [(:name a) (:name b) (:name c)]
   [ (:version a) (:version b) (:version c)]
   (:data b)
   (:data c)))

(defn- abc-more-recent-than? [[a1 b1 c1][a2 b2 c2]]
  (or (and (> a1 a2) (>= b1 b2) (>= c1 c2))
      (and (> b1 b2) (>= a1 a2) (>= c1 c2))
      (and (> c1 c2) (>= a1 a2) (>= b1 b2))))

(deftest test-join-abc
  (is (= (->ABC [:a1 :b1 :c1] [0 0 0] "b-data" "c-data") 
	 (join-abc (->A :a1 0 :b :c)(->B :b1 0 :b "b-data")(->C :c1 0 :c "c-data")))))

(deftest test-join-model
  (let [as (versioned-optimistic-map-model (str :as) :name :version >)
	bs (versioned-optimistic-map-model (str :bs) :name :version >)
	cs (versioned-optimistic-map-model (str :cs) :name :version >)
	joined-model (versioned-optimistic-map-model (str :joined) :name :version abc-more-recent-than?)
	as-joined-model (versioned-optimistic-map-model (str :as-joined) :name :version >)
	as-unjoined-model (versioned-optimistic-map-model (str :as-unjoined) :name :version >)
	a-b-joined-model (versioned-optimistic-map-model (str :a-b-joined) :name :version >)
	a-b-unjoined-model (versioned-optimistic-map-model (str :a-b-unjoined) :name :version >)
	joined-model (versioned-optimistic-map-model (str :joined) :name :version abc-more-recent-than?)
	join (join-model
              "join-model"
              joined-model
              [[as :name [:fk-b :fk-c] as-joined-model as-unjoined-model]
               [bs :name :b {} a-b-joined-model a-b-unjoined-model]
               [cs :name :c]]
              join-abc :name)
	view (test-view "test")
	a1 (->A :a1 0 :b :c)
	a1v1 (->A :a1 1 :b :c)
	a1v2 (->A :a1 2 :z :c)
	a1v3 (->A :a1 3 :b :c)
	a2 (->A :a2 0 :b :c)
	b1 (->B :b1 0 :b "b1-data")
	b1v1 (->B :b1 1 :b "b1v1-data")
	b1v2 (->B :b1 2 :z "b1v2-data")
	b1v3 (->B :b1 3 :b "b1v3-data")
	b2 (->B :b2 0 :b "b2-data")
	c1 (->C :c1 0 :c "c1-data")
	c1v1 (->C :c1 1 :c "c1v1-data")
	c1v2 (->C :c1 2 :z "c1v2-data")
	c1v3 (->C :c1 3 :c "c1v3-data")
	c2 (->C :c2 0 :c "c2-data")]
    (is (= [[{}{}{}] [[{}{}][{}{}]] [{}{}{}]] (data join)))
    (is (= {} (data joined-model)))
    (is (= {} (data a-b-joined-model)))
    (is (= {} (data a-b-unjoined-model)))

    (attach join view)
    (is (= nil (data view)))

    ;; rhs insertion - no join
    (on-upsert bs b1)
    (is (= [[{}{}{}] [[{:b1 b1}{:b {:b1 b1}}][{}{}]] [{}{}{}]] (data join)))
    (is (= {} (data joined-model)))
    (is (= {} (data a-b-joined-model)))
    (is (= {:b1 b1} (data a-b-unjoined-model)))

    ;; rhs insertion - no join
    (on-upsert cs c1)
    (is (= [[{}{}{}] [[{:b1 b1}{:b {:b1 b1}}][{:c1 c1}{:c {:c1 c1}}]] [{}{}{}]] (data join)))
    (is (= {} (data joined-model)))
    (is (= {} (data a-b-joined-model)))
    (is (= {:b1 b1} (data a-b-unjoined-model)))

    ;; lhs-insertion - first join
    (on-upsert as a1)
    (is (= [[{:a1 a1}{:b {:a1 a1}}{:c {:a1 a1}}]
            [[{:b1 b1}{:b {:b1 b1}}][{:c1 c1}{:c {:c1 c1}}]]
            [
             {:a1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [0 0 0] "b1-data" "c1-data")}}
             {:b1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [0 0 0] "b1-data" "c1-data")}}
             {:c1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [0 0 0] "b1-data" "c1-data")}}
            ]
            ]
           (data join)))
    (is (= {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [0 0 0] "b1-data" "c1-data")} (data joined-model)))
    ;;(is (= {:b1 b1} (data a-b-joined-model)))

    ;; join - new rhs - b2
    (on-upsert bs b2)
    (is (=
         [
          [{:a1 a1}{:b {:a1 a1}}{:c {:a1 a1}}]
          [[{:b1 b1 :b2 b2}{:b {:b1 b1 :b2 b2}}][{:c1 c1}{:c {:c1 c1}}]]
          [{:a1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [0 0 0] "b1-data" "c1-data")
                 [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [0 0 0] "b2-data" "c1-data")}}
           {:b1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [0 0 0] "b1-data" "c1-data")}
            :b2 {[:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [0 0 0] "b2-data" "c1-data")}}
           {:c1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [0 0 0] "b1-data" "c1-data")
                 [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [0 0 0] "b2-data" "c1-data")}}]
          ] (data join)))
    (is (= 
         {
         [:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [0 0 0] "b1-data" "c1-data")
         [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [0 0 0] "b2-data" "c1-data")
         }
         (data joined-model)))
    ;;(is (= {:b1 b1 :b2 b2} (data a-b-joined-model)))
    (is (= {:b2 b2} (data a-b-joined-model)))
    ;;(is (= {} (data a-b-unjoined-model)))
    (is (= {:b1 b1} (data a-b-unjoined-model)))

    ;; join - new rhs - c2
    (on-upsert cs c2)
    (is (= [[{:a1 a1}{:b {:a1 a1}}{:c {:a1 a1}}]
            [[{:b1 b1 :b2 b2}{:b {:b1 b1 :b2 b2}}][{:c1 c1 :c2 c2}{:c {:c1 c1 :c2 c2}}]]
            [{:a1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [0 0 0] "b1-data" "c1-data")
                   [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [0 0 0] "b2-data" "c1-data")
                   [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [0 0 0] "b1-data" "c2-data")
                   [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [0 0 0] "b2-data" "c2-data")}}
             {:b1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [0 0 0] "b1-data" "c1-data")
                   [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [0 0 0] "b1-data" "c2-data")}
              :b2 {[:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [0 0 0] "b2-data" "c1-data")
                   [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [0 0 0] "b2-data" "c2-data")}}
             {:c1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [0 0 0] "b1-data" "c1-data")
                   [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [0 0 0] "b2-data" "c1-data")}
              :c2 {[:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [0 0 0] "b1-data" "c2-data")
                   [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [0 0 0] "b2-data" "c2-data")}}]
            ] (data join)))
    (is (= {
           [:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [0 0 0] "b1-data" "c1-data")
           [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [0 0 0] "b2-data" "c1-data")
           [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [0 0 0] "b1-data" "c2-data")
           [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [0 0 0] "b2-data" "c2-data")
           }
           (data joined-model)))
    ;;(is (= {:b1 b1 :b2 b2} (data a-b-joined-model)))
    (is (= {:b2 b2} (data a-b-joined-model)))
    ;;(is (= {} (data a-b-unjoined-model)))
    (is (= {:b1 b1} (data a-b-unjoined-model)))
    
    ;; join - new lhs - a2
    (on-upsert as a2)
    (is (=
         [
          [{:a1 a1 :a2 a2}{:b {:a1 a1 :a2 a2}}{:c {:a1 a1 :a2 a2}}]
          [[{:b1 b1 :b2 b2}{:b {:b1 b1 :b2 b2}}][{:c1 c1 :c2 c2}{:c {:c1 c1 :c2 c2}}]]
          [{:a1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [0 0 0] "b1-data" "c1-data")
                 [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [0 0 0] "b2-data" "c1-data")
                 [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [0 0 0] "b1-data" "c2-data")
                 [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [0 0 0] "b2-data" "c2-data")}
            :a2 {[:a2 :b1 :c1] (->ABC [:a2 :b1 :c1] [0 0 0] "b1-data" "c1-data")
                 [:a2 :b2 :c1] (->ABC [:a2 :b2 :c1] [0 0 0] "b2-data" "c1-data")
                 [:a2 :b1 :c2] (->ABC [:a2 :b1 :c2] [0 0 0] "b1-data" "c2-data")
                 [:a2 :b2 :c2] (->ABC [:a2 :b2 :c2] [0 0 0] "b2-data" "c2-data")}}
           {:b1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [0 0 0] "b1-data" "c1-data")
                 [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [0 0 0] "b1-data" "c2-data")
                 [:a2 :b1 :c1] (->ABC [:a2 :b1 :c1] [0 0 0] "b1-data" "c1-data")
                 [:a2 :b1 :c2] (->ABC [:a2 :b1 :c2] [0 0 0] "b1-data" "c2-data")}
            :b2 {[:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [0 0 0] "b2-data" "c1-data")
                 [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [0 0 0] "b2-data" "c2-data")
                 [:a2 :b2 :c1] (->ABC [:a2 :b2 :c1] [0 0 0] "b2-data" "c1-data")
                 [:a2 :b2 :c2] (->ABC [:a2 :b2 :c2] [0 0 0] "b2-data" "c2-data")}}
           {:c1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [0 0 0] "b1-data" "c1-data")
                 [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [0 0 0] "b2-data" "c1-data")
                 [:a2 :b1 :c1] (->ABC [:a2 :b1 :c1] [0 0 0] "b1-data" "c1-data")
                 [:a2 :b2 :c1] (->ABC [:a2 :b2 :c1] [0 0 0] "b2-data" "c1-data")}
            :c2 {[:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [0 0 0] "b1-data" "c2-data")
                 [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [0 0 0] "b2-data" "c2-data")
                 [:a2 :b1 :c2] (->ABC [:a2 :b1 :c2] [0 0 0] "b1-data" "c2-data")
                 [:a2 :b2 :c2] (->ABC [:a2 :b2 :c2] [0 0 0] "b2-data" "c2-data")}}]
          ] (data join)))
    (is (= {
    	   [:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [0 0 0] "b1-data" "c1-data")
    	   [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [0 0 0] "b2-data" "c1-data")
    	   [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [0 0 0] "b1-data" "c2-data")
    	   [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [0 0 0] "b2-data" "c2-data")
    	   [:a2 :b1 :c1] (->ABC [:a2 :b1 :c1] [0 0 0] "b1-data" "c1-data")
    	   [:a2 :b2 :c1] (->ABC [:a2 :b2 :c1] [0 0 0] "b2-data" "c1-data")
    	   [:a2 :b1 :c2] (->ABC [:a2 :b1 :c2] [0 0 0] "b1-data" "c2-data")
    	   [:a2 :b2 :c2] (->ABC [:a2 :b2 :c2] [0 0 0] "b2-data" "c2-data")
    	   }
    	   (data joined-model)))
    ;;(is (= {:b1 b1 :b2 b2} (data a-b-joined-model)))
    (is (= {:b2 b2} (data a-b-joined-model)))
    ;;(is (= {} (data a-b-unjoined-model)))
    (is (= {:b1 b1} (data a-b-unjoined-model)))

    ;; lhs update - relevant joins should update
    (on-upsert as a1v1)
    (is (=
         [
          [{:a1 a1v1 :a2 a2}{:b {:a1 a1v1 :a2 a2}}{:c {:a1 a1v1 :a2 a2}}]
          [[{:b1 b1 :b2 b2}{:b {:b1 b1 :b2 b2}}][{:c1 c1 :c2 c2}{:c {:c1 c1 :c2 c2}}]]
          [{:a1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 0 0] "b1-data" "c1-data")
                 [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [1 0 0] "b2-data" "c1-data")
                 [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [1 0 0] "b1-data" "c2-data")
                 [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [1 0 0] "b2-data" "c2-data")}
            :a2 {[:a2 :b1 :c1] (->ABC [:a2 :b1 :c1] [0 0 0] "b1-data" "c1-data")
                 [:a2 :b2 :c1] (->ABC [:a2 :b2 :c1] [0 0 0] "b2-data" "c1-data")
                 [:a2 :b1 :c2] (->ABC [:a2 :b1 :c2] [0 0 0] "b1-data" "c2-data")
                 [:a2 :b2 :c2] (->ABC [:a2 :b2 :c2] [0 0 0] "b2-data" "c2-data")}}
           {:b1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 0 0] "b1-data" "c1-data")
                 [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [1 0 0] "b1-data" "c2-data")
                 [:a2 :b1 :c1] (->ABC [:a2 :b1 :c1] [0 0 0] "b1-data" "c1-data")
                 [:a2 :b1 :c2] (->ABC [:a2 :b1 :c2] [0 0 0] "b1-data" "c2-data")}
            :b2 {[:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [1 0 0] "b2-data" "c1-data")
                 [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [1 0 0] "b2-data" "c2-data")
                 [:a2 :b2 :c1] (->ABC [:a2 :b2 :c1] [0 0 0] "b2-data" "c1-data")
                 [:a2 :b2 :c2] (->ABC [:a2 :b2 :c2] [0 0 0] "b2-data" "c2-data")}}
           {:c1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 0 0] "b1-data" "c1-data")
                 [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [1 0 0] "b2-data" "c1-data")
                 [:a2 :b1 :c1] (->ABC [:a2 :b1 :c1] [0 0 0] "b1-data" "c1-data")
                 [:a2 :b2 :c1] (->ABC [:a2 :b2 :c1] [0 0 0] "b2-data" "c1-data")}
            :c2 {[:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [1 0 0] "b1-data" "c2-data")
                 [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [1 0 0] "b2-data" "c2-data")
                 [:a2 :b1 :c2] (->ABC [:a2 :b1 :c2] [0 0 0] "b1-data" "c2-data")
                 [:a2 :b2 :c2] (->ABC [:a2 :b2 :c2] [0 0 0] "b2-data" "c2-data")}}]
          ] (data join)))
    (is (= {
    	   [:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 0 0] "b1-data" "c1-data")
    	   [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [1 0 0] "b2-data" "c1-data")
    	   [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [1 0 0] "b1-data" "c2-data")
    	   [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [1 0 0] "b2-data" "c2-data")
    	   [:a2 :b1 :c1] (->ABC [:a2 :b1 :c1] [0 0 0] "b1-data" "c1-data")
    	   [:a2 :b2 :c1] (->ABC [:a2 :b2 :c1] [0 0 0] "b2-data" "c1-data")
    	   [:a2 :b1 :c2] (->ABC [:a2 :b1 :c2] [0 0 0] "b1-data" "c2-data")
    	   [:a2 :b2 :c2] (->ABC [:a2 :b2 :c2] [0 0 0] "b2-data" "c2-data")
    	   }
    	   (data joined-model)))
    ;;;(is (= {:b1 b1 :b2 b2} (data a-b-joined-model)))
    (is (= {:b2 b2} (data a-b-joined-model)))
    ;;(is (= {} (data a-b-unjoined-model)))
    (is (= {:b1 b1} (data a-b-unjoined-model)))

    ;; rhs update - relevant joins should update
    (on-upsert bs b1v1)
    (is (= [[{:a1 a1v1 :a2 a2}{:b {:a1 a1v1 :a2 a2}}{:c {:a1 a1v1 :a2 a2}}]
    	    [[{:b1 b1v1 :b2 b2}{:b {:b1 b1v1 :b2 b2}}][{:c1 c1 :c2 c2}{:c {:c1 c1 :c2 c2}}]]
            [{:a1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 1 0] "b1v1-data" "c1-data")
                   [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [1 0 0] "b2-data" "c1-data")
                   [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [1 1 0] "b1v1-data" "c2-data")
                   [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [1 0 0] "b2-data" "c2-data")}
              :a2 {[:a2 :b1 :c1] (->ABC [:a2 :b1 :c1] [0 1 0] "b1v1-data" "c1-data")
                   [:a2 :b2 :c1] (->ABC [:a2 :b2 :c1] [0 0 0] "b2-data" "c1-data")
                   [:a2 :b1 :c2] (->ABC [:a2 :b1 :c2] [0 1 0] "b1v1-data" "c2-data")
                   [:a2 :b2 :c2] (->ABC [:a2 :b2 :c2] [0 0 0] "b2-data" "c2-data")}}
             {:b1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 1 0] "b1v1-data" "c1-data")
                   [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [1 1 0] "b1v1-data" "c2-data")
                   [:a2 :b1 :c1] (->ABC [:a2 :b1 :c1] [0 1 0] "b1v1-data" "c1-data")
                   [:a2 :b1 :c2] (->ABC [:a2 :b1 :c2] [0 1 0] "b1v1-data" "c2-data")}
              :b2 {[:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [1 0 0] "b2-data" "c1-data")
                   [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [1 0 0] "b2-data" "c2-data")
                   [:a2 :b2 :c1] (->ABC [:a2 :b2 :c1] [0 0 0] "b2-data" "c1-data")
                   [:a2 :b2 :c2] (->ABC [:a2 :b2 :c2] [0 0 0] "b2-data" "c2-data")}}
             {:c1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 1 0] "b1v1-data" "c1-data")
                   [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [1 0 0] "b2-data" "c1-data")
                   [:a2 :b1 :c1] (->ABC [:a2 :b1 :c1] [0 1 0] "b1v1-data" "c1-data")
                   [:a2 :b2 :c1] (->ABC [:a2 :b2 :c1] [0 0 0] "b2-data" "c1-data")}
              :c2 {[:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [1 1 0] "b1v1-data" "c2-data")
                   [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [1 0 0] "b2-data" "c2-data")
                   [:a2 :b1 :c2] (->ABC [:a2 :b1 :c2] [0 1 0] "b1v1-data" "c2-data")
                   [:a2 :b2 :c2] (->ABC [:a2 :b2 :c2] [0 0 0] "b2-data" "c2-data")}}]
            ] (data join)))
    (is (= {
    	   [:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 1 0] "b1v1-data" "c1-data")
    	   [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [1 0 0] "b2-data" "c1-data")
    	   [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [1 1 0] "b1v1-data" "c2-data")
    	   [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [1 0 0] "b2-data" "c2-data")
    	   [:a2 :b1 :c1] (->ABC [:a2 :b1 :c1] [0 1 0] "b1v1-data" "c1-data")
    	   [:a2 :b2 :c1] (->ABC [:a2 :b2 :c1] [0 0 0] "b2-data" "c1-data")
    	   [:a2 :b1 :c2] (->ABC [:a2 :b1 :c2] [0 1 0] "b1v1-data" "c2-data")
    	   [:a2 :b2 :c2] (->ABC [:a2 :b2 :c2] [0 0 0] "b2-data" "c2-data")
    	   }
    	   (data joined-model)))
    ;;(is (= {:b1 b1v1 :b2 b2} (data a-b-joined-model)))
    (is (= {:b2 b2} (data a-b-joined-model)))
    ;;;(is (= {} (data a-b-unjoined-model)))
    (is (= {:b1 b1v1} (data a-b-unjoined-model))) ;;; WIERD - I expected this to fix previoous lhs join

    ;; rhs update - relevant joins should update
    (on-upsert cs c1v1)
    (is (= [[{:a1 a1v1 :a2 a2}{:b {:a1 a1v1 :a2 a2}}{:c {:a1 a1v1 :a2 a2}}]
    	    [[{:b1 b1v1 :b2 b2}{:b {:b1 b1v1 :b2 b2}}][{:c1 c1v1 :c2 c2}{:c {:c1 c1v1 :c2 c2}}]]
            [{:a1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 1 1] "b1v1-data" "c1v1-data")
                   [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [1 0 1] "b2-data" "c1v1-data")
                   [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [1 1 0] "b1v1-data" "c2-data")
                   [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [1 0 0] "b2-data" "c2-data")}
              :a2 {[:a2 :b1 :c1] (->ABC [:a2 :b1 :c1] [0 1 1] "b1v1-data" "c1v1-data")
                   [:a2 :b2 :c1] (->ABC [:a2 :b2 :c1] [0 0 1] "b2-data" "c1v1-data")
                   [:a2 :b1 :c2] (->ABC [:a2 :b1 :c2] [0 1 0] "b1v1-data" "c2-data")
                   [:a2 :b2 :c2] (->ABC [:a2 :b2 :c2] [0 0 0] "b2-data" "c2-data")}}
             {:b1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 1 1] "b1v1-data" "c1v1-data")
                   [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [1 1 0] "b1v1-data" "c2-data")
                   [:a2 :b1 :c1] (->ABC [:a2 :b1 :c1] [0 1 1] "b1v1-data" "c1v1-data")
                   [:a2 :b1 :c2] (->ABC [:a2 :b1 :c2] [0 1 0] "b1v1-data" "c2-data")}
              :b2 {[:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [1 0 1] "b2-data" "c1v1-data")
                   [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [1 0 0] "b2-data" "c2-data")
                   [:a2 :b2 :c1] (->ABC [:a2 :b2 :c1] [0 0 1] "b2-data" "c1v1-data")
                   [:a2 :b2 :c2] (->ABC [:a2 :b2 :c2] [0 0 0] "b2-data" "c2-data")}}
             {:c1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 1 1] "b1v1-data" "c1v1-data")
                   [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [1 0 1] "b2-data" "c1v1-data")
                   [:a2 :b1 :c1] (->ABC [:a2 :b1 :c1] [0 1 1] "b1v1-data" "c1v1-data")
                   [:a2 :b2 :c1] (->ABC [:a2 :b2 :c1] [0 0 1] "b2-data" "c1v1-data")}
              :c2 {[:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [1 1 0] "b1v1-data" "c2-data")
                   [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [1 0 0] "b2-data" "c2-data")
                   [:a2 :b1 :c2] (->ABC [:a2 :b1 :c2] [0 1 0] "b1v1-data" "c2-data")
                   [:a2 :b2 :c2] (->ABC [:a2 :b2 :c2] [0 0 0] "b2-data" "c2-data")}}]
            ] (data join)))
    (is (= {
    	   [:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 1 1] "b1v1-data" "c1v1-data")
    	   [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [1 0 1] "b2-data" "c1v1-data")
    	   [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [1 1 0] "b1v1-data" "c2-data")
    	   [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [1 0 0] "b2-data" "c2-data")
    	   [:a2 :b1 :c1] (->ABC [:a2 :b1 :c1] [0 1 1] "b1v1-data" "c1v1-data")
    	   [:a2 :b2 :c1] (->ABC [:a2 :b2 :c1] [0 0 1] "b2-data" "c1v1-data")
    	   [:a2 :b1 :c2] (->ABC [:a2 :b1 :c2] [0 1 0] "b1v1-data" "c2-data")
    	   [:a2 :b2 :c2] (->ABC [:a2 :b2 :c2] [0 0 0] "b2-data" "c2-data")
    	   }
    	   (data joined-model)))
    (is (= {:b2 b2} (data a-b-joined-model)))
    ;;;(is (= {} (data a-b-unjoined-model)))
    (is (= {:b1 b1v1} (data a-b-unjoined-model)))

    ;; join - delete lhs - a2
    (on-delete as a2)
    (is (= [[{:a1 a1v1}{:b {:a1 a1v1}}{:c {:a1 a1v1}}]
    	    [[{:b1 b1v1 :b2 b2}{:b {:b1 b1v1 :b2 b2}}][{:c1 c1v1 :c2 c2}{:c {:c1 c1v1 :c2 c2}}]]
            [{:a1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 1 1] "b1v1-data" "c1v1-data")
                   [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [1 0 1] "b2-data" "c1v1-data")
                   [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [1 1 0] "b1v1-data" "c2-data")
                   [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [1 0 0] "b2-data" "c2-data")}}
             {:b1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 1 1] "b1v1-data" "c1v1-data")
                   [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [1 1 0] "b1v1-data" "c2-data")}
              :b2 {[:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [1 0 1] "b2-data" "c1v1-data")
                   [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [1 0 0] "b2-data" "c2-data")}}
             {:c1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 1 1] "b1v1-data" "c1v1-data")
                   [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [1 0 1] "b2-data" "c1v1-data")}
              :c2 {[:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [1 1 0] "b1v1-data" "c2-data")
                   [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [1 0 0] "b2-data" "c2-data")}}]
            ] (data join)))
    (is (= {
    	   [:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 1 1] "b1v1-data" "c1v1-data")
    	   [:a1 :b2 :c1] (->ABC [:a1 :b2 :c1] [1 0 1] "b2-data" "c1v1-data")
    	   [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [1 1 0] "b1v1-data" "c2-data")
    	   [:a1 :b2 :c2] (->ABC [:a1 :b2 :c2] [1 0 0] "b2-data" "c2-data")
    	   }
    	   (data joined-model)))
    (is (= {:b2 b2} (data a-b-joined-model)))
    ;;(is (= {} (data a-b-unjoined-model)))
    (is (= {:b1 b1v1} (data a-b-unjoined-model)))

    ;; join - delete rhs - b2
    (on-delete bs b2)
    (is (= [[{:a1 a1v1}{:b {:a1 a1v1}} {:c {:a1 a1v1}}]
    	    [[{:b1 b1v1}{:b {:b1 b1v1}}][{:c1 c1v1 :c2 c2}{:c {:c1 c1v1 :c2 c2}}]]
    	    [{:a1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 1 1] "b1v1-data" "c1v1-data")
                   [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [1 1 0] "b1v1-data" "c2-data")}}
             {:b1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 1 1] "b1v1-data" "c1v1-data")
                   [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [1 1 0] "b1v1-data" "c2-data")}}
             {:c1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 1 1] "b1v1-data" "c1v1-data")}
              :c2 {[:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [1 1 0] "b1v1-data" "c2-data")}}]
            ] (data join)))
    (is (= {
    	   [:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 1 1] "b1v1-data" "c1v1-data")
    	   [:a1 :b1 :c2] (->ABC [:a1 :b1 :c2] [1 1 0] "b1v1-data" "c2-data")
    	   }
    	   (data joined-model)))
    ;;    (is (= {:b1 b1v1} (data a-b-joined-model)))
    ;(is (= {} (data a-b-joined-model)))
    ;;    (is (= {:b2 b2} (data a-b-unjoined-model)))
    ;(is (= {:b1 b1v1 :b2 b2} (data a-b-unjoined-model)))
    
    ;; join - delete rhs - c2
    (on-delete cs c2)
    (is (= [[{:a1 a1v1}{:b {:a1 a1v1}} {:c {:a1 a1v1}}]
    	    [[{:b1 b1v1}{:b {:b1 b1v1}}][{:c1 c1v1}{:c {:c1 c1v1}}]]
    	    [{:a1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 1 1] "b1v1-data" "c1v1-data")}}
             {:b1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 1 1] "b1v1-data" "c1v1-data")}}
             {:c1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 1 1] "b1v1-data" "c1v1-data")}}]
            ] (data join)))
    (is (= {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [1 1 1] "b1v1-data" "c1v1-data")}
    	   (data joined-model)))

    ;; join - amend-out lhs - a1v2
    (on-upsert as a1v2)
    (is (= [[{:a1 a1v2}{:z {:a1 a1v2}}{:c {:a1 a1v2}}]
    	    [[{:b1 b1v1}{:b {:b1 b1v1}}][{:c1 c1v1}{:c {:c1 c1v1}}]]
    	    [{}{}{}]
            ] (data join)))
    (is (= {}
    	   (data joined-model)))

    ;; join - amend in lhs - a1v3
    (on-upsert as a1v3)
    (is (= [[{:a1 a1v3}{:b {:a1 a1v3}} {:c {:a1 a1v3}}]
    	    [[{:b1 b1v1}{:b {:b1 b1v1}}][{:c1 c1v1}{:c {:c1 c1v1}}]]
    	    [{:a1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [3 1 1] "b1v1-data" "c1v1-data")}}
             {:b1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [3 1 1] "b1v1-data" "c1v1-data")}}
             {:c1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [3 1 1] "b1v1-data" "c1v1-data")}}]
            ] (data join)))
    (is (= {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [3 1 1] "b1v1-data" "c1v1-data")}
    	   (data joined-model)))

    ;; join - amend-out rhs - b1v2
    (on-upsert bs b1v2)
    (is (= [[{:a1 a1v3}{:b {:a1 a1v3}}{:c {:a1 a1v3}}]
    	    [[{:b1 b1v2}{:z {:b1 b1v2}}][{:c1 c1v1}{:c {:c1 c1v1}}]]
    	    [{}{}{}]
            ] (data join)))
    (is (= {}
    	   (data joined-model)))

    ;; join - amend-in rhs - b1v3
    (on-upsert bs b1v3)
    (is (= [[{:a1 a1v3}{:b {:a1 a1v3}} {:c {:a1 a1v3}}]
    	    [[{:b1 b1v3}{:b {:b1 b1v3}}][{:c1 c1v1}{:c {:c1 c1v1}}]]
    	    [{:a1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [3 3 1] "b1v3-data" "c1v1-data")}}
             {:b1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [3 3 1] "b1v3-data" "c1v1-data")}}
             {:c1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [3 3 1] "b1v3-data" "c1v1-data")}}]
            ] (data join)))
    (is (= {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [3 3 1] "b1v3-data" "c1v1-data")}
    	   (data joined-model)))

    ;; join - amend-out rhs - c1v2
    (on-upsert cs c1v2)
    (is (= [[{:a1 a1v3}{:b {:a1 a1v3}}{:c {:a1 a1v3}}]
    	    [[{:b1 b1v3}{:b {:b1 b1v3}}][{:c1 c1v2}{:z {:c1 c1v2}}]]
    	    [{}{}{}]
            ] (data join)))
    (is (= {}
    	   (data joined-model)))

    ;; join - amend-in rhs - c1v3
    (on-upsert cs c1v3)
    (is (= [[{:a1 a1v3}{:b {:a1 a1v3}} {:c {:a1 a1v3}}]
    	    [[{:b1 b1v3}{:b {:b1 b1v3}}][{:c1 c1v3}{:c {:c1 c1v3}}]]
    	    [{:a1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [3 3 3] "b1v3-data" "c1v3-data")}}
             {:b1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [3 3 3] "b1v3-data" "c1v3-data")}}
             {:c1 {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [3 3 3] "b1v3-data" "c1v3-data")}}]
            ] (data join)))
    (is (= {[:a1 :b1 :c1] (->ABC [:a1 :b1 :c1] [3 3 3] "b1v3-data" "c1v3-data")}
    	   (data joined-model)))

    ;; TODO:

    ;; unjoined models:
    ;; need  lhs-joined/unjoined, rhs-joined/unjoined models
    ;; should the joined ones contain singleton or tuple entries ?
    ;; need to refresh views on connection to models
    ;; need to split permutation and transposition of joins into two stages so we can populate joined models

    ;;  support custom join/get/lookup fns
    ;;  support use of custom collection
    ;;; support use of custom assoc/dissoc fns
    ;;  outer joins
    ;;  support extant / extinct
    ;;  batch operations
    ;;  test listener receives correct events
    ;;  test downstream model receives correct events
    ;;  move prod code out of this file
    ;;  refactor and document
    ;;  where can I find a persistant navigable map ?
    ;;   (first (.seqFrom (sorted-map :a 1 :c 3 :d 4) :b true)) => [:c 3]
    ;;   (first (.seqFrom (sorted-map :a 1 :c 3 :d 4) :b false)) => [:a 1]
    ;;   is this enough ?
    ))

;; THOUGHTS:
;; a flatter data model would require less rebuilding each time a change takes place
;; updates should probably be a pair of [datum, deleted?]
;; update api should be update[s] and not upsert[s], delete[s]
;; I should write a reusable versioned-map component

