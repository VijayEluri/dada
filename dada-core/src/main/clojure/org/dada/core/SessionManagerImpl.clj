(ns
 org.dada.core.SessionManagerImpl
 (:use [clojure.contrib logging])
 (:require [org.dada.core SessionImpl])
 (:import
  [java.util Collection Timer TimerTask]
  [java.util.concurrent Executors ExecutorService]
  [org.dada.core Data Metadata Model Session SessionManager SessionImpl View]
  [org.dada.jms RemotingFactory]
  )
 (:gen-class
  :implements [org.dada.core.SessionManager]
  :constructors {[String javax.jms.ConnectionFactory org.dada.core.Model] []}
  :methods []
  :init init
  :state state
  :post-init post-init
  )
 )

;; TODO - why are 3 session-managers being created when we only need one ?

;;------------------------------------------------------------------------------

(defn split [f s]
  (reduce
   (fn [[y n] e]
     (if (f e)
       [(conj y e) n]
       [y (conj n e)]))
   [{} {}]
   s))

(defn detect-client-death [mutable]
  (let [threshold (- (System/currentTimeMillis) 10000)]
    (println "sweeping clients" threshold (split (fn [[k v]] (> v threshold)) (first @mutable)))))

;;------------------------------------------------------------------------------

(defn -init [^String name ^javax.jms.ConnectionFactory connection-factory ^Model metamodel]
  (let [^javax.jms.Connection connection (doto (.createConnection connection-factory) (.start))
	^javax.jms.Session jms-session (.createSession connection false (javax.jms.Session/DUPS_OK_ACKNOWLEDGE))
	num-threads 16
	^ExecutorService thread-pool (Executors/newFixedThreadPool num-threads)
	^RemotingFactory session-manager-remoting-factory (RemotingFactory. jms-session SessionManager 10000)
	^Queue session-manager-queue (.createQueue jms-session name)
	^RemotingFactory view-remoting-factory (RemotingFactory. jms-session Session 10000)
	mutable (atom [{}])
	;;^Timer death-timer (doto (Timer.) (.schedule (proxy [TimerTask][](run [] (detect-client-death mutable))) 0 10000))
	start-fn (fn [this] (.createServer2 session-manager-remoting-factory this session-manager-queue thread-pool)) ;TODO: this server should be stashed in our mutable state...
	;;close-fn (fn [] (.cancel death-timer))
	close-fn (fn []) ;TODO - retrieve server from mutable state and close it
	immutable [name metamodel close-fn start-fn]]
    [ ;; super ctor args
     []
     ;; instance state
     [immutable mutable]]))

(defn -post-init [^org.dada.core.SessionManagerImpl this & _]
  (let [[[_ _ _ start-fn]] (.state this)] (start-fn this)))

(defn remove-session [manager session]
  (println "REMOVE SESSION" manager session))

(defn ^Session -createSession [^org.dada.core.SessionManagerImpl this]
  (let [[[_ metamodel]] (.state this)
	session (SessionImpl. metamodel (fn [session] (remove-session this session)))]
    ;; need a service-factory to start up a server and return a client proxy
    ;; close session needs to shut these down :-(
    ;; need to stash this session along with server and client
  nil)
  )

(defn -close [^org.dada.core.SessionManagerImpl this]
  (println "LOCAL SESSION MANAGER - CLOSE")
  ;; TODO - close all sessions
  (let [[[_ _ close-fn]] (.state this)]
    (close-fn))
  )

;; should be able to lose most methods below here...

(defn -ping [^org.dada.core.SessionManagerImpl this ^String client-id]
  (println "LOCAL SESSION MANAGER - PING" client-id)
  ;; refresh client's timestamp
  (let [[_ mutable] (.state this)]
    (swap!
     mutable
     (fn [[clients]]
       [(assoc clients client-id (System/currentTimeMillis))]))
    true))

(defn ^String -getName [^org.dada.core.SessionManagerImpl this]
  (let [[[name ^Model metamodel]] (.state this)]
    name))

(defn ^Data -getData [^org.dada.core.SessionManagerImpl this ^String name]
  (let [[[_ ^Model metamodel]] (.state this)]
    (.getData ^Model (.find metamodel name))))

(defn ^Model -getModel [^org.dada.core.SessionManagerImpl this ^String name]
  (let [[[_ ^Model metamodel]] (.state this)]
    (.find metamodel name)))

(defn ^Model -find [^org.dada.core.SessionManagerImpl this ^Model model key]
  (let [model-name (.getName model)]
    (.find (.getModel this model-name) key)))

(defn ^Metadata -getMetadata [^org.dada.core.SessionManagerImpl this ^String name]
  (let [[[_ ^Model metamodel]] (.state this)]
    (.getMetadata ^Model (.find metamodel name))))

;; TODO: modification of our data model should be encapsulated in a fn
;; and handed off to Model so that it is all done within the scope of
;; the Model's single spin lock. This will resolve possible race and
;; ordering issues. Fn will probably need to implement a Strategy i/f
;; so that it plays ball with Java API

;; TODO: I think we will need multiple SessionManagers so that one can
;; speak Serialised POJOs, one XML etc...

(defn ^Data -registerView [^org.dada.core.SessionManagerImpl this ^Model model ^View view]
  (let [model-name (.getName model)
	[[_ ^Model metamodel] mutable] (.state this)
	^Model model (.find metamodel model-name)]
    (if (nil? model)
      (do (warn (str "no Model for name: " model-name)) nil) ;should throw Exception
      (.registerView model view))))			     ;TODO - add resource to clients map

(defn ^Data -deregisterView [^org.dada.core.SessionManagerImpl this ^Model model ^View view]
  (let [model-name (.getName model)
	[[_ ^Model metamodel] mutable] (.state this)
	^Model model (.find metamodel model-name)]
    (if (nil? model)
      (do (warn (str "no Model for name: " model-name)) nil) ;should throw Exception
      (.deregisterView model view)))) ;TODO - remove resource from clients map

;; need contrib with-ns - only contrib dependency so far...
;; (defmacro with-temp-ns-inherited [ns & body]
;;   `(with-temp-ns (use (quote ~ns)) ~@body))

;; TODO: consider memoisation of queries, unamiguous DSL, model names, etc...
;; TODO: security - arbitrary code can be evaluated here...
(defn ^Collection -query[^org.dada.core.SessionManagerImpl this namespace-name query-string]
  (info (str "QUERY: " query-string))
  (use (symbol namespace-name))		;TODO - query should be evaluated in temporary namespace
  (let [;;target-namespace (symbol namespace-name) ;TODO - should be part of SessionManager's state
	[[_ target-metamodel] mutable] (.state this)
	;; TODO: involve target-metamodel - how can we thread this through the chain ?
	;;[_ data-fn] (with-temp-ns-inherited target-namespace (eval (read-string query-string)))
	[metadata-fn data-fn] (let [query (read-string query-string)] (prn "EVALUATING:" query) (eval query))]
    [(metadata-fn)(data-fn)])) ;TODO: security - arbitrary code can be evaluated here...
