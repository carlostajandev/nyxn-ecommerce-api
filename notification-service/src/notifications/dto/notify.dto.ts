import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsIn,
  IsNotEmpty,
  IsObject,
  IsOptional,
  IsString,
  MaxLength,
} from 'class-validator';

/**
 * Request body for POST /notifications/notify.
 *
 * Channel selection is the caller's responsibility here — they know the user's
 * preferred channel (from their notification preferences) better than the service
 * does. This is distinct from POST /agent/smart-notification where Claude
 * recommends the channel based on event context.
 */
export class NotifyRequest {
  @ApiProperty({
    enum: ['email', 'push', 'sms'],
    description: 'Delivery channel. Each channel has a registered strategy.',
    example: 'email',
  })
  @IsString()
  @IsIn(['email', 'push', 'sms'], {
    message: 'channel must be one of: email, push, sms',
  })
  channel!: 'email' | 'push' | 'sms';

  @ApiProperty({
    description: 'Recipient identifier (user ID, email address, or phone number)',
    example: 'user-abc123',
  })
  @IsString()
  @IsNotEmpty()
  recipientId!: string;

  @ApiProperty({
    description: 'Notification title / subject line. Must be ≤ 60 chars for push compatibility.',
    maxLength: 60,
    example: 'New product available in Electronics',
  })
  @IsString()
  @IsNotEmpty()
  @MaxLength(60, {
    message: 'subject must be 60 characters or fewer (push notification title limit)',
  })
  subject!: string;

  @ApiProperty({
    description: 'Notification body (plain text — no markdown, no HTML).',
    example: 'The Laptop Pro 15 is now available. Grab yours before stock runs out!',
  })
  @IsString()
  @IsNotEmpty()
  body!: string;

  @ApiPropertyOptional({
    description: 'Arbitrary key-value metadata forwarded to the strategy (e.g. deep-link URL for push).',
    example: { deepLink: 'nyxn://products/abc-123' },
  })
  @IsObject()
  @IsOptional()
  metadata?: Record<string, unknown>;
}

/**
 * Response from POST /notifications/notify.
 */
export class NotifyResponse {
  @ApiProperty({ description: 'Channel that delivered the notification', example: 'email' })
  channel!: string;

  @ApiProperty({
    description: 'Provider-assigned message ID for tracing and deduplication',
    example: 'email-3f2a1b0c-...',
  })
  messageId!: string;

  @ApiProperty({
    enum: ['sent', 'queued', 'failed'],
    description:
      '"queued" means the provider accepted the message; actual delivery is asynchronous.' +
      ' "sent" means confirmed delivery (rare — requires provider webhook). ' +
      '"failed" means the provider rejected the message.',
    example: 'queued',
  })
  status!: 'sent' | 'queued' | 'failed';

  @ApiProperty({
    description: 'ISO-8601 timestamp when the strategy accepted the message',
    example: '2026-01-15T10:30:00.000Z',
  })
  dispatchedAt!: string;
}
