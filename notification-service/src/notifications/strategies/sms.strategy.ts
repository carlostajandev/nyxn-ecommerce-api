import { Injectable, Logger } from '@nestjs/common';
import { NotificationStrategy, NotifyPayload, NotifyResult } from './notification.strategy';
import { randomUUID } from 'crypto';

/**
 * SMS delivery strategy — stub implementation.
 *
 * Production replacement: inject Twilio or AWS SNS SDK, validate the recipient
 * as an E.164 phone number, and dispatch the message.
 *
 * SMS-specific constraints:
 *   - A single SMS is 160 GSM-7 characters (or 153 for multi-part messages).
 *     Bodies longer than 160 chars are split into concatenated messages by the
 *     carrier, increasing cost proportionally. Warn here if the body is too long.
 *   - recipientId for SMS is an E.164 phone number (+1234567890), not a user ID.
 *     A production implementation would resolve the user ID → phone number via
 *     a user profile service before delegating to this strategy.
 *   - Subject is not delivered in SMS — the body IS the full message.
 *     We include the subject prefix so the recipient sees the topic inline.
 */
@Injectable()
export class SmsNotificationStrategy implements NotificationStrategy {
  readonly channel = 'sms' as const;

  private static readonly SMS_CHAR_LIMIT = 160;

  private readonly logger = new Logger(SmsNotificationStrategy.name);

  async send(payload: NotifyPayload): Promise<NotifyResult> {
    const messageId = `sms-${randomUUID()}`;

    // Concatenate subject + body because SMS has no separate title field.
    const fullMessage = `${payload.subject}: ${payload.body}`;

    if (fullMessage.length > SmsNotificationStrategy.SMS_CHAR_LIMIT) {
      // Warn rather than truncate — truncation can change meaning. The caller
      // should pass shorter copy for SMS, or split into multiple messages.
      this.logger.warn(
        `SMS body is ${fullMessage.length} chars (>${SmsNotificationStrategy.SMS_CHAR_LIMIT}). ` +
          `This will be split into multiple segments by the carrier. messageId=${messageId}`,
      );
    }

    // In production: await twilioClient.messages.create({ to: payload.recipientId, from: twilioNumber, body: fullMessage });
    this.logger.log(
      `[SMS STUB] to=${payload.recipientId} length=${fullMessage.length} messageId=${messageId}`,
    );

    return {
      channel: this.channel,
      messageId,
      status: 'queued',
      dispatchedAt: new Date().toISOString(),
    };
  }
}
