package com.nyxn.ecommerce.testconfig;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base class for integration tests that require real database and cache containers.
 *
 * <p>Testcontainers starts a PostgreSQL and Redis container once per JVM process (static fields)
 * and reuses them across all test classes that extend this base — the startup cost is paid once
 * rather than per class. The {@link DynamicPropertySource} injects the dynamic container URLs into
 * the Spring environment before the application context boots, overriding the defaults in {@code
 * application.yml}.
 *
 * <p>GCP Pub/Sub, SMTP, and Stripe are external services that must be mocked at the bean level
 * ({@code @MockBean}) in each IT class — we don't run a full emulator in unit/integration scope.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class BaseIntegrationTest {

  // Static containers are shared across all test classes in the same JVM — Testcontainers
  // stops them via a shutdown hook after all tests complete.
  @Container
  @SuppressWarnings("resource")
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("nyxn_test")
          .withUsername("nyxn")
          .withPassword("nyxn");

  @Container
  @SuppressWarnings("resource")
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void registerContainerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    // Disable the GCP autoconfiguration in test scope — all GCP beans that need real credentials
    // are replaced by @MockBean in the individual IT classes.
    registry.add("spring.cloud.gcp.pubsub.enabled", () -> "false");
  }
}
