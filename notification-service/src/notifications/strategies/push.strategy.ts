import { Injectable, Logger } from '@nestjs/common';
import { NotificationStrategy, NotifyPayload, NotifyResult } from './notification.strategy';
import { randomUUID } from 'crypto';

/**
 * Push notification delivery strategy — stub implementation.
 *
 * Production replacement: inject an FCM / APNs / OneSignal client and call the
 * real push API. The recipient identifier in production would be a device token
 * (FCM registration ID) rather than a user ID.
 *
 * Push-specific constraints encoded here:
 *   - Subject/title must be ≤ 60 chars (iOS notification title limit).
 *   - Body should be ≤ 240 chars for on-screen readability. Beyond that, users
 *     see a truncated preview; the full body is only visible inside the app.
 *
 * These rules are enforced at the strategy level, not at the controller level,
 * because they are transport-specific — email has no such character limits.
 */
@Injectable()
export class PushNotificationStrategy implements NotificationStrategy {
  readonly channel = 'push' as const;

  private readonly logger = new Logger(PushNotificationStrategy.name);

  async send(payload: NotifyPayload): Promise<NotifyResult> {
    const messageId = `push-${randomUUID()}`;

    const titleTruncated = payload.subject.slice(0, 60);
    const bodyTruncated = payload.body.slice(0, 240);

    // In production: await fcmClient.send({ token: payload.recipientId, notification: { title, body } });
    this.logger.log(
      `[PUSH STUB] to=${payload.recipientId} title="${titleTruncated}" messageId=${messageId}`,
    );

    if (payload.subject.length > 60) {
      this.logger.warn(
        `Push subject truncated from ${payload.subject.length} to 60 chars for messageId=${messageId}`,
      );
    }

    return {
      channel: this.channel,
      messageId,
      status: 'queued',
      dispatchedAt: new Date().toISOString(),
    };
  }
}
