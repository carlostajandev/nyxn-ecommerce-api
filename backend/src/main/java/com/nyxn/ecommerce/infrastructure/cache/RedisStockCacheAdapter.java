package com.nyxn.ecommerce.infrastructure.cache;

import com.nyxn.ecommerce.domain.ports.out.StockCachePort;
import com.nyxn.ecommerce.domain.valueobject.ProductId;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis implementation of {@link StockCachePort}.
 *
 * <h2>Why a Lua script instead of DECRBY + check?</h2>
 *
 * <p>An approach that uses {@code DECRBY} then conditionally {@code INCRBY} to undo is not atomic
 * at the application level — there is a window between the two commands where another client sees
 * an inconsistent (possibly negative) value. A Lua script submitted via {@code EVAL} executes as a
 * single Redis command: no other client can interleave between the GET and the SET inside the
 * script. This is the only way to achieve a conditional decrement atomically without Redis
 * Transactions (MULTI/EXEC) which carry higher overhead and do not support conditionals.
 *
 * <h2>Key schema</h2>
 *
 * <pre>product:stock:{uuid} → integer (remaining units)</pre>
 *
 * <p>TTL is set to 2 hours on {@link #sync} to avoid stale keys after a Redis restart. A missing
 * key is treated as a cache miss (return value {@code -1}) — the caller falls back to the DB.
 */
@Component
public class RedisStockCacheAdapter implements StockCachePort {

  private static final Logger log = LoggerFactory.getLogger(RedisStockCacheAdapter.class);
  private static final String KEY_PREFIX = "product:stock:";
  private static final Duration STOCK_TTL = Duration.ofHours(2);

  // Lua script: atomically read current stock, check sufficiency, decrement if sufficient.
  // Returns: new stock value on success, -1 if key missing, -2 if insufficient stock.
  // The script runs inside Redis's single-threaded event loop — no other command executes
  // concurrently with it, eliminating the read-modify-write race that plain DECRBY cannot avoid.
  private static final RedisScript<Long> DECREMENT_IF_SUFFICIENT =
      RedisScript.of(
          """
          local current = tonumber(redis.call('GET', KEYS[1]))
          if current == nil then
            return -1
          end
          local requested = tonumber(ARGV[1])
          if current < requested then
            return -2
          end
          local remaining = current - requested
          redis.call('SET', KEYS[1], remaining)
          return remaining
          """,
          Long.class);

  private final RedisTemplate<String, String> redisTemplate;

  public RedisStockCacheAdapter(RedisTemplate<String, String> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public long tryDecrement(ProductId id, int quantity) {
    String key = key(id);
    Long result =
        redisTemplate.execute(DECREMENT_IF_SUFFICIENT, List.of(key), String.valueOf(quantity));

    if (result == null) {
      // Redis returned nil — unexpected, treat as cache miss
      log.warn("Nil response from Redis for key {}; treating as cache miss", key);
      return -1L;
    }

    log.debug("Redis tryDecrement key={} qty={} result={}", key, quantity, result);
    return result;
  }

  @Override
  public void increment(ProductId id, int quantity) {
    String key = key(id);
    redisTemplate.opsForValue().increment(key, quantity);
    log.debug("Redis increment key={} qty={}", key, quantity);
  }

  @Override
  public void sync(ProductId id, int stock) {
    String key = key(id);
    // SET with TTL in one command (atomic) — prevents the key from persisting indefinitely
    // after a product is deleted or stock logic changes.
    redisTemplate.opsForValue().set(key, String.valueOf(stock), STOCK_TTL);
    log.debug("Redis sync key={} stock={} ttl={}", key, stock, STOCK_TTL);
  }

  private static String key(ProductId id) {
    return KEY_PREFIX + id.getValue();
  }
}
