import { Injectable, Logger } from '@nestjs/common';
import { NotificationStrategy } from './notification.strategy';

/**
 * Slack channel strategy — OCP demonstration.
 *
 * Adding this channel required:
 *   1. This class (new file).
 *   2. Four lines in NotificationsModule (provide + NOTIFICATION_STRATEGIES entry).
 *
 * Files that did NOT change:
 *   - NotificationsController   ✅ closed
 *   - NotificationChannelRegistry ✅ closed
 *   - Any existing strategy     ✅ closed
 *
 * That is Open/Closed Principle working as intended.
 *
 * Production: post to a Slack Incoming Webhook URL or use the Slack Block Kit API.
 */
@Injectable()
export class SlackStrategy implements NotificationStrategy {
  readonly channel = 'slack';
  private readonly logger = new Logger(SlackStrategy.name);

  async execute(userId: string, message: string): Promise<void> {
    // In production: await slackClient.chat.postMessage({ channel: resolveSlackId(userId), text: message });
    this.logger.log(`[SLACK] userId=${userId} preview="${message.slice(0, 100)}"`);
  }
}
