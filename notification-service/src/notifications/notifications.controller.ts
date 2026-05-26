import {
  Body,
  Controller,
  HttpCode,
  HttpStatus,
  Logger,
  Post,
} from '@nestjs/common';
import { ApiOperation, ApiResponse, ApiTags } from '@nestjs/swagger';
import { NotificationStrategyRegistry } from './strategy.registry';
import { NotifyRequest, NotifyResponse } from './dto/notify.dto';

/**
 * REST controller for direct notification delivery.
 *
 * This endpoint complements POST /agent/smart-notification:
 *   - /agent/smart-notification  → AI picks subject, body, and channel.
 *   - /notifications/notify      → Caller provides all content; strategy delivers it.
 *
 * Typical callers:
 *   1. The backend (Spring Boot) after an order-confirmed event where content is
 *      templated (not AI-generated) for cost and latency reasons.
 *   2. Admin tooling sending manual announcements with known content.
 *   3. The Claude agent's consumer: after generating content, it calls this endpoint
 *      to dispatch on the recommended channel.
 *
 * The controller itself has no channel-specific knowledge — it delegates entirely
 * to the NotificationStrategyRegistry. Adding a new channel requires:
 *   1. Implementing a new NotificationStrategy.
 *   2. Registering it in NotificationsModule.
 *   3. No changes here.
 */
@ApiTags('notifications')
@Controller('notifications')
export class NotificationsController {
  private readonly logger = new Logger(NotificationsController.name);

  constructor(private readonly registry: NotificationStrategyRegistry) {}

  /**
   * Deliver a notification via the requested channel strategy.
   *
   * Returns 200 (not 201) because this is an action endpoint, not a resource-creation
   * endpoint. The notification message is ephemeral; there is no persistent resource
   * whose URI could be returned in a Location header.
   */
  @Post('notify')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Send a notification via a specific channel',
    description:
      'Dispatches a notification through the registered strategy for the specified channel. ' +
      'Supports: email (SMTP stub), push (FCM stub), sms (Twilio stub). ' +
      'All transports are stubbed in the demo — logs output replaces network calls.',
  })
  @ApiResponse({
    status: 200,
    description: 'Notification accepted by the channel strategy',
    type: NotifyResponse,
  })
  @ApiResponse({ status: 400, description: 'Validation failed (missing fields, subject too long)' })
  @ApiResponse({ status: 404, description: 'Channel strategy not registered' })
  async notify(@Body() request: NotifyRequest): Promise<NotifyResponse> {
    this.logger.log(
      `Dispatch requested — channel=${request.channel} recipient=${request.recipientId}`,
    );

    // Registry throws NotFoundException for unknown channels — NestJS converts
    // it to a 404 automatically via the built-in exception filter.
    const strategy = this.registry.get(request.channel);

    const result = await strategy.send({
      recipientId: request.recipientId,
      subject: request.subject,
      body: request.body,
      metadata: request.metadata,
    });

    this.logger.log(
      `Notification dispatched — channel=${result.channel} messageId=${result.messageId} status=${result.status}`,
    );

    return {
      channel: result.channel,
      messageId: result.messageId,
      status: result.status,
      dispatchedAt: result.dispatchedAt,
    };
  }
}
