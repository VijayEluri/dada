/*
 * Copyright (c) 2009, Julian Gosnell
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.dada.core;

import java.util.Collection;
import java.util.Collections;

// TODO: should be some sort of TransformedModelView - since output is a different shape from input

public class AggregatedModelView<KI, VI, V, KO, VO> extends AbstractModel<KO, VO> implements View<VI> {

	public interface Aggregator<VI, V, KO, VO> {
		V initialValue();
		Class<?> initialType(Class<?> type);
		VO currentValue(KO key, int version, V value);
		V aggregate(Collection<Update<VI>> insertions, Collection<Update<VI>> updates, Collection<Update<VI>> deletions);
		V apply(V currentValue, V delta);
	}

	private final Aggregator<VI, V, KO, VO> aggregator;

	private final Collection<Update<VO>> nil = Collections.emptyList();
	
	private final KO key;
	private int version;
	private V value;
	
	public AggregatedModelView(String name, Metadata<KO, VO> metadata, KO key, Aggregator<VI, V, KO, VO> aggregator) {
		super(name, metadata);
		this.key = key;
		this.aggregator = aggregator;
		version = 0;
		value = this.aggregator.initialValue();
	}

	@Override
	public Collection<VO> getData() {
		int snapshotVersion;
		V snapshotValue;
		synchronized (value) {
			snapshotVersion = version;
			snapshotValue = value;
		}
		return Collections.singleton(aggregator.currentValue(key, snapshotVersion, snapshotValue));
	}

	@Override
	public void update(Collection<Update<VI>> insertions, Collection<Update<VI>> updates, Collection<Update<VI>> deletions) {
		V delta = aggregator.aggregate(insertions, updates, deletions);
		V oldValue, newValue;
		int oldVersion, newVersion;
		synchronized (value) {
			oldVersion = version;
			oldValue = value;
			newVersion = ++version;
			newValue = value = aggregator.apply(value, delta);
		}
		notifyUpdate(nil, Collections.singleton(new Update<VO>(aggregator.currentValue(key, oldVersion, oldValue), aggregator.currentValue(key, newVersion, newValue))), nil);
	}

}