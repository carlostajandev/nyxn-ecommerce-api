import { IsIn, IsNotEmpty, IsOptional, IsString } from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

/**
 * Envelope format published by the Spring Boot backend's PubSubProductEventPublisher.
 *
 * This DTO must stay in sync with the Java record:
 *   PubSubProductEventPublisher.ProductEvent(eventType, entityId, payload)
 *
 * In a production system this contract would be formalised as an Avro or Protobuf
 * schema registered in a schema registry, ensuring both producer and consumer
 * validate against the same version. Without a registry, a producer-side change can
 * silently break this consumer.
 */
export class ProductEventDto {
  @ApiProperty({
    enum: ['PRODUCT_CREATED', 'PRODUCT_UPDATED', 'PRODUCT_DELETED'],
    description: 'Domain event type from the e-commerce backend',
  })
  @IsString()
  @IsIn(['PRODUCT_CREATED', 'PRODUCT_UPDATED', 'PRODUCT_DELETED'])
  eventType!: string;

  @ApiProperty({ description: 'UUID of the product that triggered the event' })
  @IsString()
  @IsNotEmpty()
  entityId!: string;

  @ApiPropertyOptional({ description: 'Full product payload (null for PRODUCT_DELETED)' })
  @IsOptional()
  payload?: Record<string, unknown> | null;
}

/**
 * Request body for the smart notification endpoint (Section 6B).
 */
export class SmartNotificationRequest {
  @ApiProperty({ description: 'Product event triggering the notification' })
  event!: ProductEventDto;

  @ApiPropertyOptional({
    description: 'Target audience context for personalisation (e.g. "premium customer")',
    default: 'general customer',
  })
  @IsString()
  @IsOptional()
  audienceContext?: string;
}

/**
 * Response from the Claude-powered smart notification endpoint.
 */
export class SmartNotificationResponse {
  @ApiProperty({ description: 'Event type that triggered this notification' })
  eventType!: string;

  @ApiProperty({ description: 'Product ID the notification refers to' })
  productId!: string;

  @ApiProperty({ description: 'AI-generated notification subject line (≤ 60 chars)' })
  subject!: string;

  @ApiProperty({ description: 'AI-generated notification body (plain text)' })
  body!: string;

  @ApiProperty({ description: 'Suggested delivery channel: email | push | sms' })
  channel!: string;

  @ApiProperty({ description: 'Claude model used to generate the content' })
  model!: string;
}
