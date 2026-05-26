package com.nyxn.ecommerce.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis cache configuration with per-cache TTL, jitter-based stampede prevention, and a type-safe
 * registry of cache names.
 *
 * <h2>Per-cache TTL partitions</h2>
 *
 * <p>Different data sets have fundamentally different update frequencies and staleness tolerances.
 * A single global TTL forces a trade-off no value can satisfy well. {@link
 * RedisCacheManager#withInitialCacheConfigurations} assigns an optimal TTL per named cache; the
 * global default acts as a safety net for any unnamed cache added in the future.
 *
 * <pre>
 *  Cache name               Base TTL   Jitter     Rationale
 *  ──────────────────────── ─────────  ─────────  ──────────────────────────────────────────────────
 *  products                 45 min     ± 5 min    Catalog updates are infrequent; long TTL amortises
 *                                                 expensive entity-graph hydration across many reads.
 *  analytics:top-products   15 min     ± 90 s     Window-function query; aligns with dashboard cadence.
 *  analytics:revenue-trend  15 min     ± 90 s     Each cache gets an independent jitter offset so the
 *  analytics:low-stock      15 min     ± 90 s     three don't all expire at the same second.
 * </pre>
 *
 * <h2>TTL jitter (cache stampede prevention)</h2>
 *
 * <p>Without jitter, entries created during startup cache warming all expire simultaneously. Under
 * Cyber Day load, simultaneous expiry triggers a thundering herd: every in-flight request sees a
 * miss and races to query the DB. Per-cache random jitter distributes those expirations across a
 * window — the DB sees a gradual drip of misses rather than a spike. Zero extra infrastructure.
 *
 * <p>{@link ThreadLocalRandom} is used instead of {@code java.util.Random}: each thread maintains
 * its own state, eliminating CAS contention on a shared seed — important under concurrent load.
 *
 * <h2>Value serialization</h2>
 *
 * <p>JSON with embedded {@code @class} type info — readable in {@code redis-cli} for debugging,
 * free from {@code serialVersionUID} brittleness, and consumable by future polyglot services.
 */
@Configuration
@EnableCaching
public class RedisConfig {

  // ─── Cache name registry ───────────────────────────────────────────────────
  // Declared here — the authority for what named caches exist — and imported by adapters using
  // @Cacheable / @CacheEvict. Centralising the strings makes a rename a single-file change
  // and enables IDE "find usages" across the codebase.
  public static final String CACHE_PRODUCTS = "products";
  public static final String CACHE_ANALYTICS_TOP = "analytics:top-products";
  public static final String CACHE_ANALYTICS_REVENUE = "analytics:revenue-trend";
  public static final String CACHE_ANALYTICS_LOW_STOCK = "analytics:low-stock";

  // ─── TTL parameters ────────────────────────────────────────────────────────
  private static final Duration PRODUCT_TTL = Duration.ofMinutes(45);
  private static final int PRODUCT_JITTER_SECONDS = 300; // ±5 minutes

  private static final Duration ANALYTICS_TTL = Duration.ofMinutes(15);
  private static final int ANALYTICS_JITTER_SECONDS = 90; // ±90 seconds

  @Bean
  public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
    GenericJackson2JsonRedisSerializer serializer = buildSerializer();

    // Each cache gets its own jitteredTtl() call — independent random offsets ensure
    // the three analytics caches don't all expire at the same moment even when all three
    // are warmed simultaneously at startup by CacheWarmupService.
    RedisCacheConfiguration productCfg =
        cacheConfig(serializer, jitteredTtl(PRODUCT_TTL, PRODUCT_JITTER_SECONDS));
    RedisCacheConfiguration topProductsCfg =
        cacheConfig(serializer, jitteredTtl(ANALYTICS_TTL, ANALYTICS_JITTER_SECONDS));
    RedisCacheConfiguration revenueCfg =
        cacheConfig(serializer, jitteredTtl(ANALYTICS_TTL, ANALYTICS_JITTER_SECONDS));
    RedisCacheConfiguration lowStockCfg =
        cacheConfig(serializer, jitteredTtl(ANALYTICS_TTL, ANALYTICS_JITTER_SECONDS));

    return RedisCacheManager.builder(connectionFactory)
        // Fallback for any @Cacheable not in the explicit map — defaults to product TTL.
        // Prevents an accidentally-added cache from using an unbounded or incorrect TTL.
        .cacheDefaults(productCfg)
        .withInitialCacheConfigurations(
            Map.of(
                CACHE_PRODUCTS, productCfg,
                CACHE_ANALYTICS_TOP, topProductsCfg,
                CACHE_ANALYTICS_REVENUE, revenueCfg,
                CACHE_ANALYTICS_LOW_STOCK, lowStockCfg))
        .build();
  }

  // ─── Private helpers ───────────────────────────────────────────────────────

  private static GenericJackson2JsonRedisSerializer buildSerializer() {
    ObjectMapper mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            // NON_FINAL typing embeds the concrete class name in every JSON document so
            // deserialisation can reconstruct the correct subtype without per-field type hints.
            .activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
    return new GenericJackson2JsonRedisSerializer(mapper);
  }

  /**
   * Builds a {@link RedisCacheConfiguration} with JSON serialisation, string keys, and no null
   * caching.
   *
   * <p>Null caching is disabled globally: caching a null result silently hides data-not-found bugs
   * and makes cache debugging misleading. Add {@code unless = "#result == null"} on individual
   * {@code @Cacheable} annotations only if a specific cache must tolerate nulls.
   */
  private static RedisCacheConfiguration cacheConfig(
      GenericJackson2JsonRedisSerializer serializer, Duration ttl) {
    return RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(ttl)
        .serializeKeysWith(
            RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
        .disableCachingNullValues();
  }

  /**
   * Returns {@code base ± jitterSeconds} seconds using {@link ThreadLocalRandom}.
   *
   * <p>Jitter is symmetric so the expected (mean) TTL equals {@code base}. The range is chosen wide
   * enough to spread expiry across many seconds but narrow enough that the actual TTL remains
   * semantically correct (no risk of negative TTL or doubling).
   */
  private static Duration jitteredTtl(Duration base, int jitterSeconds) {
    int offset = ThreadLocalRandom.current().nextInt(-jitterSeconds, jitterSeconds);
    return base.plusSeconds(offset);
  }
}
