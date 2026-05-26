import { Injectable, Logger } from '@nestjs/common';
import { NotificationStrategy } from './notification.strategy';

/**
 * Push notification channel strategy.
 * Production: replace with FCM / APNs / OneSignal SDK.
 * Note: userId should resolve to a device token in the real implementation.
 */
@Injectable()
export class PushStrategy implements NotificationStrategy {
  readonly channel = 'push';
  private readonly logger = new Logger(PushStrategy.name);

  async execute(userId: string, message: string): Promise<void> {
    // In production: await fcmClient.send({ token: resolveDeviceToken(userId), notification: { body: message } });
    this.logger.log(`[PUSH] userId=${userId} preview="${message.slice(0, 60)}"`);
  }
}
