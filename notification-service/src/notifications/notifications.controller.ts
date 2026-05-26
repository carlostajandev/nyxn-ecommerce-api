import { Body, Controller, HttpCode, HttpStatus, Logger, Post } from '@nestjs/common';
import { ApiOperation, ApiResponse, ApiTags } from '@nestjs/swagger';
import { NotificationChannelRegistry } from './registry/notification-channel.registry';
import { NotifyRequest, NotifyResponse } from './dto/notify.dto';

/**
 * POST /notify — dispatch a notification via the selected channel strategy.
 *
 * This controller has a single responsibility: validate input, delegate to the
 * registry, and shape the response. It contains no channel-specific logic and
 * never changes when a new channel is added — Open/Closed Principle in practice.
 *
 * @Controller('') — empty prefix so the route is exactly /notify.
 * An alternative (@Controller('notifications') + @Post('notify')) would expose
 * /notifications/notify, which does not match the spec.
 */
@ApiTags('notifications')
@Controller('')
export class NotificationsController {
  private readonly logger = new Logger(NotificationsController.name);

  constructor(private readonly registry: NotificationChannelRegistry) {}

  @Post('notify')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Send a notification via a channel strategy',
    description:
      'Resolves the registered strategy for the given channel and executes it. ' +
      'Returns 400 if the channel is not registered. ' +
      'Currently registered channels: email, sms, push, slack.',
  })
  @ApiResponse({ status: 200, description: 'Notification dispatched', type: NotifyResponse })
  @ApiResponse({ status: 400, description: 'Validation error or unknown channel' })
  async notify(@Body() request: NotifyRequest): Promise<NotifyResponse> {
    this.logger.log(`notify channel=${request.channel} userId=${request.userId}`);

    // Registry throws BadRequestException (400) for unknown channels.
    const strategy = this.registry.resolve(request.channel);
    await strategy.execute(request.userId, request.message);

    return { success: true, channel: request.channel, userId: request.userId };
  }
}
