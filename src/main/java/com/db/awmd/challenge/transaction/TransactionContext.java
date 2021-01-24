package com.db.awmd.challenge.transaction;

import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionContext<K, V> {
	@Getter
	private Map<K, V> savePoints = new ConcurrentHashMap<>();
}
