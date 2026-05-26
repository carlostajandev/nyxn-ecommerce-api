import { Injectable, Logger } from '@nestjs/common';
import { NotificationStrategy } from './notification.strategy';

/**
 * Email channel strategy.
 * Production: replace the logger stub with an SMTP client (nodemailer / SendGrid SDK).
 */
@Injectable()
export class EmailStrategy implements NotificationStrategy {
  readonly channel = 'email';
  private readonly logger = new Logger(EmailStrategy.name);

  async execute(userId: string, message: string): Promise<void> {
    // In production: await smtpClient.sendMail({ to: resolveEmail(userId), text: message });
    this.logger.log(`[EMAIL] userId=${userId} preview="${message.slice(0, 80)}"`);
  }
}
