import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { TerminusModule } from '@nestjs/terminus';
import { NotificationsModule } from './notifications/notifications.module';
import { PubSubModule } from './pubsub/pubsub.module';
import { AgentModule } from './agent/agent.module';
import { HealthController } from './health/health.controller';

/**
 * Root application module.
 *
 * ConfigModule.forRoot({ isGlobal: true }) makes ConfigService available in every
 * module without re-importing it — avoids a forFeature() call in every module just
 * to read environment variables. The envFilePath order gives .env.local priority
 * over .env, which mirrors how most teams manage local overrides vs committed defaults.
 */
@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: ['.env.local', '.env'],
    }),
    TerminusModule,
    PubSubModule,
    NotificationsModule,
    AgentModule,
  ],
  controllers: [HealthController],
})
export class AppModule {}
