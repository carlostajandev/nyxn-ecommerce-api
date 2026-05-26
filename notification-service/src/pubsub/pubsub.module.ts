import { Module, Global } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { PubSub } from '@google-cloud/pubsub';

/**
 * Global module that provides a single PubSub client instance.
 *
 * Why Global: the PubSub client maintains a gRPC connection pool. Creating one
 * instance per module would open redundant connections and consume extra file
 * descriptors. A single shared instance (like a database connection pool) is the
 * correct pattern.
 *
 * Emulator support: when PUBSUB_EMULATOR_HOST is set in the environment, the
 * Google Cloud client library automatically routes all calls to the local emulator
 * instead of the real GCP API. No code-level switch is needed — the library reads
 * the env var directly. This is the same mechanism used by the Spring Boot backend.
 *
 * In production, GCP credentials are provided via the Application Default
 * Credentials chain (GOOGLE_APPLICATION_CREDENTIALS env var or workload identity
 * on GKE/Cloud Run). No credentials config is needed here — the library resolves
 * them at runtime.
 */
@Global()
@Module({
  imports: [ConfigModule],
  providers: [
    {
      provide: PubSub,
      useFactory: (config: ConfigService): PubSub => {
        const projectId = config.get<string>('GCP_PROJECT_ID', 'local-project');
        return new PubSub({ projectId });
      },
      inject: [ConfigService],
    },
  ],
  exports: [PubSub],
})
export class PubSubModule {}
