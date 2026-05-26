package com.nyxn.ecommerce.domain.ports.out;

import com.nyxn.ecommerce.domain.valueobject.ProductId;

/**
 * Outbound port: fast in-memory stock reservation layer.
 *
 * <p>The Redis adapter behind this port serves as the first serialisation point for concurrent
 * reservation requests. Because Redis is single-threaded and processes commands atomically, many
 * concurrent callers that would otherwise collide at the PostgreSQL row lock are resolved instantly
 * at this layer — only the small number that succeed here ever reach the database.
 *
 * <p>This port is deliberately narrow. It does not represent the source of truth for stock (the
 * database is). It represents a fast, atomic gate that reduces the thundering herd on PostgreSQL
 * during peak traffic events like Cyber-Day.
 *
 * <p>Consistency model: Redis stock and PostgreSQL stock are eventually consistent. The Redis value
 * is decremented before the DB commit; if the DB write fails after all retries the Redis decrement
 * is reversed (compensating transaction). Cache misses (key absent) fall back to the DB-only path
 * transparently.
 */
public interface StockCachePort {

  /**
   * Atomically decrements the cached stock by {@code quantity} if sufficient units are available.
   *
   * <p>The implementation uses a Lua script executed as a single Redis command — there is no window
   * between the read and the write where another caller can interleave. The result is:
   *
   * <ul>
   *   <li>Positive or zero: reservation succeeded; the returned value is the remaining stock.
   *   <li>{@code -1}: cache miss — the key does not exist; caller falls back to the DB path.
   *   <li>{@code -2}: insufficient stock in cache.
   * </ul>
   *
   * @param id the product whose stock is reserved
   * @param quantity the number of units to reserve
   * @return remaining stock after decrement, {@code -1} on cache miss, {@code -2} if insufficient
   */
  long tryDecrement(ProductId id, int quantity);

  /**
   * Atomically adds {@code quantity} back to the cached stock.
   *
   * <p>Called as a compensating operation when the downstream DB write fails after retries,
   * ensuring the cache does not permanently under-count available stock.
   *
   * @param id the product whose stock is released
   * @param quantity the number of units to return
   */
  void increment(ProductId id, int quantity);

  /**
   * Overwrites the cached stock value, aligning the cache with the DB.
   *
   * <p>Called when a product is created or when an admin updates stock directly. Without periodic
   * or event-driven synchronisation, the Redis value could drift from the DB value (e.g. after a
   * Redis restart or a direct DB update that bypassed the cache layer).
   *
   * @param id the product to synchronise
   * @param stock the current authoritative stock value from the database
   */
  void sync(ProductId id, int stock);
}
