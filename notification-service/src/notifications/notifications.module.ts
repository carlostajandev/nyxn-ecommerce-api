import { Module } from '@nestjs/common';
import { NotificationsController } from './notifications.controller';
import { NotificationsService } from './notifications.service';
import { NotificationChannelRegistry } from './registry/notification-channel.registry';
import {
  NOTIFICATION_STRATEGIES,
  NotificationStrategy,
} from './strategies/notification.strategy';
import { EmailStrategy } from './strategies/email.strategy';
import { SmsStrategy } from './strategies/sms.strategy';
import { PushStrategy } from './strategies/push.strategy';
import { SlackStrategy } from './strategies/slack.strategy';

/**
 * NotificationsModule wires three independent concerns:
 *
 *   1. NotificationsService     — Pub/Sub subscriber (processes inbound events).
 *   2. NotificationsController  — POST /notify (dispatches on demand via HTTP).
 *   3. NotificationChannelRegistry — resolves channel → strategy at request time.
 *
 * NestJS does not expose an Angular-style `multi: true` property on its Provider
 * types. The idiomatic NestJS equivalent is a single useFactory provider that
 * receives every concrete strategy via `inject` and returns them as an array.
 * The registry is injected with that array under NOTIFICATION_STRATEGIES.
 *
 * Adding a new notification channel (e.g. WhatsApp):
 *   1. Create WhatsAppStrategy implements NotificationStrategy.
 *   2. Add WhatsAppStrategy to the providers array (concrete class).
 *   3. Add WhatsAppStrategy to the useFactory inject array and the factory return.
 *   Done. Zero changes to NotificationsController, NotificationChannelRegistry,
 *   or any existing strategy — Open/Closed Principle is maintained for all
 *   classes that are NOT NotificationsModule.
 */
@Module({
  controllers: [NotificationsController],
  providers: [
    NotificationsService,

    // ── Concrete strategy singletons ────────────────────────────────────────
    // Registered individually so they can be injected by class token in tests.
    EmailStrategy,
    SmsStrategy,
    PushStrategy,
    SlackStrategy,

    // ── Strategy collection for the registry ────────────────────────────────
    // useFactory receives the four singletons above (same DI instances, not copies)
    // and exposes them as a typed array under NOTIFICATION_STRATEGIES.
    // The registry constructor receives this array via @Inject(NOTIFICATION_STRATEGIES).
    {
      provide: NOTIFICATION_STRATEGIES,
      useFactory: (
        email: EmailStrategy,
        sms: SmsStrategy,
        push: PushStrategy,
        slack: SlackStrategy,
      ): NotificationStrategy[] => [email, sms, push, slack],
      inject: [EmailStrategy, SmsStrategy, PushStrategy, SlackStrategy],
    },

    // ── Registry ────────────────────────────────────────────────────────────
    NotificationChannelRegistry,
  ],
  exports: [NotificationsService, NotificationChannelRegistry],
})
export class NotificationsModule {}
