import { Injectable, Logger } from '@nestjs/common';
import { NotificationStrategy, NotifyPayload, NotifyResult } from './notification.strategy';
import { randomUUID } from 'crypto';

/**
 * Email delivery strategy — stub implementation.
 *
 * Production replacement: inject an SMTP client (nodemailer, SendGrid SDK, SES)
 * and swap this stub body for the real transport call. The interface contract
 * (NotificationStrategy) and the registry lookup are untouched by that change.
 *
 * Why a stub and not a real SMTP call in this demo?
 *   The technical test exercises the architecture (Strategy Pattern, DI, testability)
 *   rather than third-party integrations. A real SMTP call would require credentials,
 *   a sandbox inbox, and network access — all of which are outside the test scope.
 *   The stub logs the payload so the behaviour is observable in container logs.
 */
@Injectable()
export class EmailNotificationStrategy implements NotificationStrategy {
  readonly channel = 'email' as const;

  private readonly logger = new Logger(EmailNotificationStrategy.name);

  async send(payload: NotifyPayload): Promise<NotifyResult> {
    const messageId = `email-${randomUUID()}`;

    // In production: await smtpClient.sendMail({ to: payload.recipientId, subject: payload.subject, text: payload.body });
    this.logger.log(
      `[EMAIL STUB] to=${payload.recipientId} subject="${payload.subject}" messageId=${messageId}`,
    );

    return {
      channel: this.channel,
      messageId,
      // Stub always returns 'queued': real transports accept the message and
      // deliver asynchronously. 'sent' would mean confirmed delivery — only
      // webhooks/callbacks from the provider can confirm that.
      status: 'queued',
      dispatchedAt: new Date().toISOString(),
    };
  }
}
