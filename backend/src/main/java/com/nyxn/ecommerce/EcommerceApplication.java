package com.nyxn.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Application entry point.
 *
 * <p>{@code @EnableCaching} activates Spring's cache abstraction, wired to Redis via {@code
 * spring.cache.type=redis} in application.yml.
 *
 * <p>{@code @EnableAsync} registers Spring's task executor so that {@code @Async} methods in {@code
 * OrderAsyncProcessor} actually run on background threads.
 *
 * <p>{@code @EnableRetry} activates the AspectJ-based retry interceptor that processes
 * {@code @Retryable} / {@code @Recover} annotations in {@code ProductStockUpdater}. Without this,
 * those annotations are silently ignored.
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableRetry
public class EcommerceApplication {

  public static void main(String[] args) {
    SpringApplication.run(EcommerceApplication.class, args);
  }
}
