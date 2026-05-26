import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString } from 'class-validator';

/**
 * Request body for POST /notify.
 *
 * Channel validation is intentionally absent here (@IsIn is not used).
 * Reason: encoding the valid channel list in the DTO means every new channel
 * would require changing this file — a violation of the Open/Closed Principle.
 * The NotificationChannelRegistry handles channel validation and throws a
 * BadRequestException (HTTP 400) for unknown values, which NestJS converts
 * to the same JSON error body as a class-validator failure.
 */
export class NotifyRequest {
  @ApiProperty({
    description: 'Target user identifier',
    example: 'user-abc123',
  })
  @IsString()
  @IsNotEmpty()
  userId!: string;

  @ApiProperty({
    description: 'Notification message body',
    example: 'Your order has been confirmed and will arrive within 3-5 business days.',
  })
  @IsString()
  @IsNotEmpty()
  message!: string;

  @ApiProperty({
    description: 'Delivery channel. Currently registered: email, sms, push, slack.',
    example: 'email',
  })
  @IsString()
  @IsNotEmpty()
  channel!: string;
}

export class NotifyResponse {
  @ApiProperty({ example: true })
  success!: boolean;

  @ApiProperty({ example: 'email' })
  channel!: string;

  @ApiProperty({ example: 'user-abc123' })
  userId!: string;
}
