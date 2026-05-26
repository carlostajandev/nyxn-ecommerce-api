package com.nyxn.ecommerce.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.Random;
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
 * Configuracion de Redis Cache.
 *
 * <p>TTL base: 45 minutos. Justificacion: los productos cambian como maximo 1 vez por hora en
 * operacion normal. 45 min da margen de seguridad sin impactar UX. Cacheamos por ID individual
 * (granularidad fina) para evitar invalidaciones masivas.
 *
 * <p>Jitter TTL: +/- 5 minutos aleatorios sobre el base. Esto mitiga el Cache Stampede — si N items
 * expiran exactamente al mismo tiempo bajo alta carga (Cyber-Day), todos los threads van a base de
 * datos simultaneamente. Con jitter, las expiraciones se distribuyen en el tiempo.
 */
@Configuration
@EnableCaching
public class RedisConfig {

  private static final Duration BASE_TTL = Duration.ofMinutes(45);
  private static final int JITTER_SECONDS = 300; // +/- 5 minutos

  @Bean
  public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
    ObjectMapper objectMapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

    GenericJackson2JsonRedisSerializer serializer =
        new GenericJackson2JsonRedisSerializer(objectMapper);

    Duration ttlWithJitter =
        BASE_TTL.plusSeconds(new Random().nextInt(JITTER_SECONDS * 2) - JITTER_SECONDS);

    RedisCacheConfiguration config =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(ttlWithJitter)
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer))
            .disableCachingNullValues();

    return RedisCacheManager.builder(connectionFactory).cacheDefaults(config).build();
  }
}
