package com.nomura.cash;

public class CurrencyManagerImpl extends PositionManagerImpl<Currency, Trade> implements CurrencyManager {

	public CurrencyManagerImpl(Currency currency) {
		super(currency);
	}
}