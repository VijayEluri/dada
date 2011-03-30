(ns 
 #^{:author "Jules Gosnell" :doc "Demo Server for DADA"}
 org.dada.demo.server
 (:use
  [org.dada web]
  [org.dada core])
 (:import
  [java.util.concurrent
   Executors
   ExecutorService]
  [javax.jms
   ConnectionFactory]
  [org.springframework.context.support
   ClassPathXmlApplicationContext]
  [org.springframework.beans.factory
   BeanFactory]
  [org.dada.core
   SessionManager
   SessionManagerNameGetter
   SessionManagerImpl
   View
   ViewNameGetter]
  [org.dada.jms
   MessageStrategy
   BytesMessageStrategy
   SerializeTranslator
   Translator
   ServiceFactory
   JMSServiceFactory]
  [org.springframework.beans.factory
   BeanFactory])
 )

(if (not *compile-files*)
  (do
    (let [^ConnectionFactory connection-factory (.getBean #^BeanFactory (ClassPathXmlApplicationContext. "server.xml") "connectionFactory")
	  ^javax.jms.Connection connection (doto (.createConnection connection-factory) (.start))
	  ^javax.jms.Session jms-session (.createSession connection false (javax.jms.Session/DUPS_OK_ACKNOWLEDGE))
	  num-threads 16		;TODO - hardwired
	  timeout 10000			;TODO - hardwired
	  ^ExecutorService thread (Executors/newFixedThreadPool num-threads)
	  ^MessageStrategy strategy (BytesMessageStrategy.)
	  ^Translator translator (SerializeTranslator.)
	  ^ServiceFactory service-factory (JMSServiceFactory. jms-session thread strategy translator timeout)]
      (def ^SessionManager session-manager (SessionManagerImpl. "SessionManager.POJO" service-factory *metamodel*)))

    ;; move this into SessionManagerImpl
    (def jetty (start-jetty 8888))
    ))
