/**
 * 
 */
package org.omo.jms;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.UUID;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractClient implements MessageListener, Serializable {

	protected long timeout;
	protected Class<?> interfaze;
	protected Destination destination;
	protected boolean trueAsync;

	protected transient Logger logger;
	protected transient UUID uuid;
	protected transient Session session;
	protected transient MessageProducer producer;
	protected transient Queue resultsQueue;
	protected transient MessageConsumer consumer;
	protected /* final */ SimpleMethodMapper mapper;
	protected transient int count; // used by subclasses
	
	public AbstractClient(Session session, Destination destination, Class<?> interfaze, long timeout, boolean trueAsync) throws JMSException {
		init(session, destination, interfaze, timeout, trueAsync);
	}
	
	protected void init(Session session, Destination destination, Class<?> interfaze, long timeout, boolean trueAsync) throws JMSException {
		logger = LoggerFactory.getLogger(getClass());
		this.interfaze = interfaze;
		mapper = new SimpleMethodMapper(interfaze);
		this.destination = destination;
		this.trueAsync = trueAsync;
		this.timeout = timeout;
		this.uuid = UUID.randomUUID();

		this.session = session;
		this.producer = session.createProducer(destination);
		this.resultsQueue = session.createQueue(interfaze.getCanonicalName() + "." + uuid);
		this.consumer = session.createConsumer(resultsQueue);
		this.consumer.setMessageListener(this);
	}

	//@Override
	private void writeObject(ObjectOutputStream oos) throws IOException {
		oos.writeObject(destination);
		oos.writeObject(interfaze);
		oos.writeLong(timeout);
		oos.writeBoolean(trueAsync);
	}
	
	//@Override
	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		Session session = getCurrentSession();
		Destination destination = (Destination)ois.readObject();
		Class<?> interfaze= (Class<?>)ois.readObject();
		long timeout = ois.readLong();
		boolean trueAsync = ois.readBoolean();
		try {
			init(session, destination, interfaze, timeout, trueAsync);
		} catch (JMSException e) {
			logger.error("unexpected problem reconstructing client proxy", e);
		}
	}
	
	protected final static ThreadLocal<Session> currentSession = new ThreadLocal<Session>(); // TODO: encapsulate

	public static void setCurrentSession(Session session) {
		currentSession.set(session);
	}

	public static Session getCurrentSession() {
		return currentSession.get();
	}
	

}