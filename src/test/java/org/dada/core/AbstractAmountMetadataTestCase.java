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

import java.math.BigDecimal;

import junit.framework.TestCase;

public class AbstractAmountMetadataTestCase extends TestCase {

	public void test() {
		Metadata<Integer, Amount> metadata = new AbstractAmountMetadata<Integer, Amount>("Key", "Amount") {
			@Override
			protected BigDecimal getAmount(Amount value) {
				return value.getAmount();
			}
		};
		
		BigDecimal one = new BigDecimal("1.0");
		Amount amount = new Amount(1, 0, one);
		
		assertTrue(metadata.getAttributeNames().size() == 3);
		assertTrue(metadata.getKey(amount) == 1);
		assertTrue(metadata.getAttributeValue(amount, 0).equals(1));
		assertTrue(metadata.getAttributeValue(amount, 1).equals(0));
		assertTrue(metadata.getAttributeValue(amount, 2).equals(one));
		assertTrue(metadata.getAttributeValue(amount, 3) == null);
	}
}
