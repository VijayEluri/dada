/**
 * 
 */
package org.omo.core;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class FilterView<T> extends AbstractModel<T> implements ModelView<T, T> {

	// Lifecycle
	
	public void start() {
	}

	public void stop() {
	}
	
	protected final Query<T> query;
	protected final LinkedList<T> results;
	
	public FilterView(String name, Query<T> query) {
		super(name);
		this.query = query;
		this.results = new LinkedList<T>();
	}
	
	// Model
	
	protected Collection<T> getData() {
		return results;
	}
	
	// TODO: is there a difference between insertions and updates ?
	// TODO: how can we handle them efficiently in a concurrent environment

	// Listener
	
	@Override
	public void upsert(T upsertion) {
		if (query.apply(upsertion)) {
			results.addFirst(upsertion);
			for (View<T> listener : views)
				listener.upsert(upsertion);
		}
	}

	@Override
	public void upsert(Collection<T> upsertions) {
		List<T> relevantUpdates = query.apply(upsertions);
		if (!relevantUpdates.isEmpty())
			for (View<T> view : views)
				view.upsert(relevantUpdates);
	}

	@Override
	public void delete(Collection<Integer> deletions) {
		throw new UnsupportedOperationException("NYI");
	}

	@Override
	public void delete(int deletion) {
		throw new UnsupportedOperationException("NYI");
	}
}
