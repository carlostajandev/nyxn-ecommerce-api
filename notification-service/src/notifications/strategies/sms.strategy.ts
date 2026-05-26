import { Injectable, Logger } from '@nestjs/common';
import { NotificationStrategy } from './notification.strategy';

/**
 * SMS channel strategy.
 * Production: replace the stub with Twilio or AWS SNS.
 * Note: userId should resolve to an E.164 phone number in the real implementation.
 */
@Injectable()
export class SmsStrategy implements NotificationStrategy {
  readonly channel = 'sms';
  private readonly logger = new Logger(SmsStrategy.name);

  async execute(userId: string, message: string): Promise<void> {
    // In production: await twilioClient.messages.create({ to: resolvePhone(userId), body: message });
    this.logger.log(`[SMS] userId=${userId} chars=${message.length}`);
  }
}
