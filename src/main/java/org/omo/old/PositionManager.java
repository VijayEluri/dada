package org.omo.old;

/**
 * A PositionManager is responsible for rolling up a set of Positions into an aggregate Position.
 * 
 * @author jules
 *
 * @param <T>
 */
public interface PositionManager<I extends Identifiable, T extends Position> extends Manager<I, T>, Position {
	
	void registerPositionListener(Listener<Integer> listener);
	void deregisterPositionListener(Listener<Integer> listener);
}