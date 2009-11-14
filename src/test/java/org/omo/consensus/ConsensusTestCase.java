package org.omo.consensus;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Session;

import junit.framework.TestCase;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.omo.jms.AsyncInvocationListener;
import org.omo.jms.AsynchronousClient;
import org.omo.jms.RemotingFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ConsensusTestCase extends TestCase {

	private static final Logger LOG = LoggerFactory.getLogger(ConsensusTestCase.class);
	private ConnectionFactory connectionFactory;
	private Connection connection;
	private Session session;
	private Destination destination;
	private RemotingFactory<Paxos> remotingFactory;
	
	public static interface Paxos {
		int foo();
	};
	
	public static class PaxosImpl implements Paxos {
		public int foo() { return 1;}
	};

	protected void setUp() throws Exception {
		super.setUp();
		connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false&broker.useJmx=false");
		connection = connectionFactory.createConnection();
		connection.start();
		session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		destination = session.createTopic(Paxos.class.getCanonicalName());
		int timeout = 5000;
		remotingFactory = new RemotingFactory<Paxos>(session, Paxos.class, timeout);
	}

	protected void tearDown() throws Exception {
		session.close();
		session = null;
		connection.stop();
		connection.close();
		connection = null;
		connectionFactory = null;
		super.tearDown();
	}
	
	public void testTopic() throws Exception {
		ExecutorService executorService =  new ThreadPoolExecutor(10, 100, 600, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(100));
		Paxos server1 = remotingFactory.createServer(new PaxosImpl(), destination, executorService);
		Paxos server2 = remotingFactory.createServer(new PaxosImpl(), destination, executorService);
		Paxos server3 = remotingFactory.createServer(new PaxosImpl(), destination, executorService);
		Paxos server4 = remotingFactory.createServer(new PaxosImpl(), destination, executorService);
		AsynchronousClient client = remotingFactory.createAsynchronousClient(destination, true);
		
		client.invoke(Paxos.class.getMethod("foo", (Class<?>[])null), null, new AsyncInvocationListener(){

			@Override
			public void onError(Exception exception) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onResult(Object value) {
				LOG.info("foo = " + value);
			}});

		Thread.sleep(5000);
	}

}
