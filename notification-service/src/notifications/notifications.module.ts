import { Module } from '@nestjs/common';
import { NotificationsController } from './notifications.controller';
import { NotificationsService } from './notifications.service';
import { NotificationChannelRegistry } from './registry/notification-channel.registry';
import { NOTIFICATION_STRATEGIES } from './strategies/notification.strategy';
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
 * Adding a new notification channel (e.g. WhatsApp):
 *   1. Create WhatsAppStrategy implements NotificationStrategy.
 *   2. Add two lines in this file (provide + multi entry).
 *   3. Done. Zero changes to controller, registry, or existing strategies.
 */
@Module({
  controllers: [NotificationsController],
  providers: [
    NotificationsService,

    // ── Channel strategies ──────────────────────────────────────────────────
    // Each strategy is bound twice:
    //   As a concrete class  → enables typed injection in unit tests.
    //   As NOTIFICATION_STRATEGIES (multi: true) → registry receives the array.
    EmailStrategy,
    SmsStrategy,
    PushStrategy,
    SlackStrategy, // OCP demo — added without touching controller or registry

    { provide: NOTIFICATION_STRATEGIES, useExisting: EmailStrategy,  multi: true },
    { provide: NOTIFICATION_STRATEGIES, useExisting: SmsStrategy,    multi: true },
    { provide: NOTIFICATION_STRATEGIES, useExisting: PushStrategy,   multi: true },
    { provide: NOTIFICATION_STRATEGIES, useExisting: SlackStrategy,  multi: true },

    // ── Registry ────────────────────────────────────────────────────────────
    NotificationChannelRegistry,
  ],
  exports: [NotificationsService, NotificationChannelRegistry],
})
export class NotificationsModule {}
