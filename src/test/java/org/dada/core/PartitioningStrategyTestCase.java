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

import java.util.ArrayList;
import java.util.Collection;

import junit.framework.TestCase;

public class PartitioningStrategyTestCase extends TestCase {

	public class TestView implements View<Datum<Integer>> {
		@Override
		public void update(Collection<Update<Datum<Integer>>> insertions, Collection<Update<Datum<Integer>>> alterations, Collection<Update<Datum<Integer>>> deletions) {
			throw new UnsupportedOperationException("NYI");
		}
	};

	public void test() {
		Collection<View<Datum<Integer>>> views = new ArrayList<View<Datum<Integer>>>();
		TestView view0 = new TestView();
		views.add(view0);
		TestView view1 = new TestView();
		views.add(view1);
		Getter<Integer, Datum<Integer>> getter = new Getter<Integer, Datum<Integer>>() {
			@Override
			public Integer get(Datum<Integer> value) {
				return value.getId();
			}
		};
		PartitioningStrategy<Integer, Datum<Integer>> strategy = new PartitioningStrategy<Integer, Datum<Integer>>(getter, views);

		assertFalse(strategy.getMutable());
		assertTrue(strategy.getRoute(new IntegerDatum(0, 0)) == 0);
		assertTrue(strategy.getRoute(new IntegerDatum(1, 0)) == 1);
		assertTrue(strategy.getRoute(new IntegerDatum(2, 0)) == 0);
		assertTrue(strategy.getRoute(new IntegerDatum(3, 0)) == 1);

		Collection<View<Datum<Integer>>> views0 = strategy.getViews(0);
		Collection<View<Datum<Integer>>> views1 = strategy.getViews(1);
		assertTrue(views0.size() == 1);
		assertTrue(views0.iterator().next() == view0);
		assertTrue(views1.size() == 1);
		assertTrue(views1.iterator().next() == view1);
	}
}
