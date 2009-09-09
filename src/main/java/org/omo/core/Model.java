package org.omo.core;

import java.util.Collection;

public interface Model<OutputKey, OutputValue> extends Lifecycle {

	String getName();
	
	Collection<OutputValue> registerView(View<OutputKey, OutputValue> view);
	void deregisterView(View<OutputKey, OutputValue> view);
	
}