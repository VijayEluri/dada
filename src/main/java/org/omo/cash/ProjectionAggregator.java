package org.omo.cash;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.omo.core.Aggregator;
import org.omo.core.DateRange;
import org.omo.core.Update;
import org.omo.core.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProjectionAggregator implements Aggregator<Projection, AccountTotal> {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final List<BigDecimal> positions;
	private final List<Date> dates;
	private final int account;
	private final View<Integer, Projection> view;

	private int version;
	
	public ProjectionAggregator(String name, DateRange dateRange, int account, View<Integer, Projection> view) {
		this.account = account;
		this.view = view;
		dates = new ArrayList<Date>(dateRange.getValues()); // TODO: clumsy - but enables index lookup - slow - should be a Map ?
		positions = new ArrayList<BigDecimal>(dates.size());
		for (Date date : dates) {
			positions.add(new BigDecimal(0));
		}
	}
	
	@Override
	public Projection getAggregate() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("NYI");
	}

	private Collection<Update<Projection>> empty = new ArrayList<Update<Projection>>(); 
	
	@Override
	public void update(Collection<Update<AccountTotal>> insertions, Collection<Update<AccountTotal>> updates, Collection<Update<AccountTotal>> deletions) {
		logger.debug("insert: size={}", insertions.size());
		logger.debug("update: size={}", updates.size());
		logger.debug("remove: size={}", 1);
		Projection oldValue2 = new Projection(account, version, new ArrayList<BigDecimal>(positions));

		for (Update<AccountTotal> insertion : insertions) {
			AccountTotal newValue = insertion.getNewValue();
			Date date = newValue.getId();
			int index = dates.indexOf(date);
			positions.set(index, newValue.getAmount());
		}
		for (Update<AccountTotal> update : updates) {
			AccountTotal oldValue = update.getOldValue();
			AccountTotal newValue = update.getNewValue();
			Date date = newValue.getId();
			int index = dates.indexOf(date);
			synchronized (positions) {
				positions.set(index, newValue.getAmount());
			}
		}
		// deletions ?
		if (deletions.size() > 0)
			throw new UnsupportedOperationException("NYI");
		
		Projection newValue2 = new Projection(account, ++version, new ArrayList<BigDecimal>(positions));
		Set<Update<Projection>> updatesOut = Collections.singleton(new Update<Projection>(oldValue2, newValue2));
		view.update(empty, updatesOut, empty); 
	}

}
