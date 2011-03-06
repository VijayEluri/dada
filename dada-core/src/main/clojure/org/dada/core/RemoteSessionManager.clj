(ns org.dada.core.RemoteSessionManager
  (:use
   [clojure.contrib logging]
   )
  (:require
   [org.dada.core.jms JMSServiceFactory POJOInvoker])
  (:import
   [java.net
    URL
    URLClassLoader]
   [java.util
    Collection]
   [java.util.concurrent
    Executors]
   [javax.jms
    Connection
    ConnectionFactory
    Session]
   [org.apache.activemq
    ActiveMQConnectionFactory]
   [org.dada.core
    Data
    Metadata
    Model
    SessionManager
    SessionManagerHelper
    SessionManagerNameGetter
    ServiceFactory
    View
    ViewNameGetter]
   [org.dada.core.jms
    JMSServiceFactory
    POJOInvoker]
   [org.dada.jms
    QueueFactory
    TopicFactory
    SimpleMethodMapper]
   )
  (:gen-class
   :implements [org.dada.core.SessionManager]
   :constructors {[String org.dada.core.Model org.dada.core.ServiceFactory] []}
   :methods []
   :init init
   :post-init post-init
   :state state
   )
  )

;; proxy for a remote session manager
;; intercept outward bound local Views and replace with proxies

(defn -init [^String session-manager-name ^Model metamodel ^ServiceFactory service-factory]

  ;; install our class-loader in hierarchy
  (let [current-thread (Thread/currentThread)]
    (.setContextClassLoader
     current-thread
     (URLClassLoader.
      (into-array [(URL. "http://localhost:8888/")])
      (.getContextClassLoader current-thread))))

  (let [^ConnectionFactory connection-factory (doto (ActiveMQConnectionFactory. "" "" "tcp://localhost:61616")
						(.setOptimizedMessageDispatch true)
						(.setObjectMessageSerializationDefered true)
						(.setWatchTopicAdvisories false))
	^Connection connection (doto (.createConnection connection-factory)
				 (.start))
	^Session  session (.createSession connection false (Session/DUPS_OK_ACKNOWLEDGE))

	^Executor thread-pool (Executors/newFixedThreadPool 2)

	^ServiceFactory session-manager-service-factory (JMSServiceFactory.
							 session
							 SessionManager
							 thread-pool
							 true
							 10000
							 (SessionManagerNameGetter.)
							 (QueueFactory.)
							 (POJOInvoker. (SimpleMethodMapper. SessionManager))
							 session-manager-name)
	^ServiceFactory view-service-factory (JMSServiceFactory.
					      session
					      View
					      thread-pool
					      true
					      10000
					      (ViewNameGetter.)
					      (TopicFactory.)
					      (POJOInvoker. (SimpleMethodMapper. View))
					      session-manager-name)
	^SessionManager peer (.client session-manager-service-factory "SessionManager")

	;; should we remember views that we have decoupled ?
	remote-view (fn [^View view] (.decouple view-service-factory view))
	]
    [ ;; super ctor args
     []
     ;; instance state
     [peer remote-view]]))

(defn -post-init [this #^Model model & _]
  (SessionManagerHelper/setCurrentSessionManager this))

(defn ^Model -find [this ^String model-name key]
  (let [[^SessionManager peer] (.state this)]
    (.find peer model-name key)))

(defn ^Data -registerView [this ^String model-name ^View view]
  (let [[^SessionManager peer remote-view] (.state this)]
    (.registerView peer model-name (remote-view view))))

(defn ^Data -deregisterView [this ^String model-name ^View view]
  (let [[^SessionManager peer remote-view] (.state this)]
    (.deregisterView peer model-name (remote-view view)))) ;stop server for this view

;; implemented - but should not have to

(defn ^Data -getData [^org.dada.core.RemoteSessionManager this ^String model-name]
  ;;warn("RemoteSessionManager: calling getData(), but should not have to")
  (let [[^SessionManager peer] (.state this)]
    (.getData peer model-name)))

;; don't implement - yet

(defn ^String -getName [^org.dada.core.RemoteSessionManager this]
  (throw (UnsupportedOperationException. "NYI")))



(defn ^Model -getModel [^org.dada.core.RemoteSessionManager this ^String name]
  (throw (UnsupportedOperationException. "NYI")))

(defn ^Metadata -getMetadata [^org.dada.core.RemoteSessionManager this ^String name]
  (throw (UnsupportedOperationException. "NYI")))

(defn ^Collection -query[^org.dada.core.RemoteSessionManager this namespace-name query-string]
  (throw (UnsupportedOperationException. "NYI")))
