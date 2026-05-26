import { Module } from '@nestjs/common';
import { NotificationsController } from './notifications.controller';
import { NotificationsService } from './notifications.service';
import { NotificationStrategyRegistry } from './strategy.registry';
import { NOTIFICATION_STRATEGIES } from './strategies/notification.strategy';
import { EmailNotificationStrategy } from './strategies/email.strategy';
import { PushNotificationStrategy } from './strategies/push.strategy';
import { SmsNotificationStrategy } from './strategies/sms.strategy';

/**
 * Notifications module — wires the Pub/Sub subscriber and the Strategy Pattern
 * for the POST /notifications/notify dispatch endpoint.
 *
 * Strategy registration uses NestJS multi-providers: each strategy class is bound
 * to the NOTIFICATION_STRATEGIES symbol token with multi:true. NestJS collects all
 * three into an array and injects it into NotificationStrategyRegistry. Adding a
 * fourth channel (e.g. WhatsApp) requires only a new entry here and a new class —
 * no changes to the controller or the registry constructor.
 */
@Module({
  controllers: [NotificationsController],
  providers: [
    NotificationsService,

    // ── Strategy multi-providers ────────────────────────────────────────────
    // Each strategy is registered twice:
    //   1. As its concrete class, so it can be injected by type in tests.
    //   2. Under the NOTIFICATION_STRATEGIES symbol token (multi:true) so the
    //      registry receives the full list in a single injection.
    EmailNotificationStrategy,
    PushNotificationStrategy,
    SmsNotificationStrategy,

    { provide: NOTIFICATION_STRATEGIES, useExisting: EmailNotificationStrategy, multi: true },
    { provide: NOTIFICATION_STRATEGIES, useExisting: PushNotificationStrategy,  multi: true },
    { provide: NOTIFICATION_STRATEGIES, useExisting: SmsNotificationStrategy,   multi: true },

    // ── Registry ────────────────────────────────────────────────────────────
    NotificationStrategyRegistry,
  ],
  exports: [NotificationsService, NotificationStrategyRegistry],
})
export class NotificationsModule {}
