package com.nyxn.ecommerce.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * GCP Secret Manager configuration.
 *
 * <p>How it works:
 * When {@code spring.cloud.gcp.secretmanager.enabled=true} (set in
 * application-prod.yml), the Spring Cloud GCP auto-configuration activates a
 * {@code GcpSecretManagerEnvironmentPostProcessor} that resolves any property
 * whose value matches the pattern {@code ${sm://projects/PROJECT/secrets/NAME}}
 * before the application context starts. This means {@code @Value} annotations
 * referencing {@code sm://} paths are transparently resolved from Secret Manager
 * — no code change is needed at the injection site.
 *
 * <p>Local development fallback:
 * In non-production profiles ({@code default}, {@code docker}), Secret Manager
 * is disabled and the {@code sm://} placeholders are never evaluated. Properties
 * fall back to environment variables or {@code .env} file values. This means
 * a developer without GCP credentials can still run the application locally.
 *
 * <p>Required GCP setup (run once per environment):
 * <pre>{@code
 * # Create secrets
 * echo -n "$DB_PASSWORD"    | gcloud secrets create db-password    --data-file=- --project=$GCP_PROJECT
 * echo -n "$REDIS_PASSWORD" | gcloud secrets create redis-password --data-file=- --project=$GCP_PROJECT
 * echo -n "$CLAUDE_API_KEY" | gcloud secrets create claude-api-key --data-file=- --project=$GCP_PROJECT
 *
 * # Grant the Cloud Run service account access
 * gcloud secrets add-iam-policy-binding db-password \
 *   --member="serviceAccount:$SA_EMAIL" \
 *   --role="roles/secretmanager.secretAccessor" \
 *   --project=$GCP_PROJECT
 * }</pre>
 *
 * <p>Security properties:
 * <ul>
 *   <li>Secret values are never logged — Spring Cloud GCP masks them in
 *       environment dumps (Actuator /env endpoint).</li>
 *   <li>Each Cloud Run revision is granted the minimum IAM role required
 *       ({@code roles/secretmanager.secretAccessor}) per service account.</li>
 *   <li>Secret versions are pinned to {@code latest} in application-prod.yml.
 *       For zero-downtime rotation, rotate the secret, deploy the new revision,
 *       then disable the old version.</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(
    name = "spring.cloud.gcp.secretmanager.enabled",
    havingValue = "true",
    matchIfMissing = false)
public class SecretManagerConfig {

  private static final Logger log = LoggerFactory.getLogger(SecretManagerConfig.class);

  /**
   * Eager validation bean: reads the DB password from Secret Manager at startup.
   * If the secret is missing or the service account lacks permission, the
   * application fails fast with a clear error instead of surfacing a credentials
   * error on the first DB connection attempt at runtime.
   *
   * <p>The property {@code spring.datasource.password} is resolved from
   * application-prod.yml's {@code sm://} reference before this bean is created,
   * so this is purely a validation probe.
   *
   * <p>In the {@code docker} and {@code default} profiles this bean is not
   * instantiated because the enclosing {@code @ConditionalOnProperty} is false.
   */
  @Bean
  @Profile("prod")
  public SecretManagerHealthProbe secretManagerHealthProbe(
      @Value("${spring.datasource.password}") String dbPassword) {

    if (dbPassword == null || dbPassword.isBlank()) {
      throw new IllegalStateException(
          "DB password resolved from Secret Manager is blank. "
              + "Verify that the secret 'db-password' exists in the GCP project "
              + "and the service account has roles/secretmanager.secretAccessor.");
    }

    log.info("Secret Manager: DB password successfully resolved.");
    return new SecretManagerHealthProbe(true);
  }

  /** Marker record returned by the probe bean — holds the validation result. */
  public record SecretManagerHealthProbe(boolean valid) {}
}
