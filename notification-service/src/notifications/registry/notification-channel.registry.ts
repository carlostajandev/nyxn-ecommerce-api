import { BadRequestException, Inject, Injectable, Logger } from '@nestjs/common';
import {
  NOTIFICATION_STRATEGIES,
  NotificationStrategy,
} from '../strategies/notification.strategy';

/**
 * Factory that resolves a NotificationStrategy by channel name at runtime.
 *
 * Design decisions:
 *
 *   Map over if-else / switch:
 *     Map<string, strategy> built once at startup → O(1) lookup per request.
 *     A switch would require a code change every time a new channel is added.
 *
 *   BadRequestException on unknown channel (not NotFoundException):
 *     An unknown channel is always a caller programming error, not a missing
 *     resource. HTTP 400 communicates "you sent a bad value"; 404 would imply
 *     there is a route we could look up if only it existed.
 *
 *   Multi-provider injection (@Inject(NOTIFICATION_STRATEGIES)):
 *     NestJS collects every provider registered with { multi: true } under this
 *     symbol and injects them as an array. The registry iterates that array once
 *     at construction time — no code change needed here when a strategy is added.
 */
@Injectable()
export class NotificationChannelRegistry {
  private readonly logger = new Logger(NotificationChannelRegistry.name);
  private readonly strategies = new Map<string, NotificationStrategy>();

  constructor(
    @Inject(NOTIFICATION_STRATEGIES)
    strategies: NotificationStrategy[],
  ) {
    for (const strategy of strategies) {
      this.strategies.set(strategy.channel, strategy);
      this.logger.log(`Channel registered: ${strategy.channel}`);
    }
  }

  /**
   * Resolve a strategy for the given channel.
   * @throws BadRequestException if the channel has no registered strategy.
   */
  resolve(channel: string): NotificationStrategy {
    const strategy = this.strategies.get(channel);

    if (!strategy) {
      const valid = [...this.strategies.keys()].join(', ');
      throw new BadRequestException(
        `Unknown channel '${channel}'. Registered channels: ${valid}`,
      );
    }

    return strategy;
  }

  /** Introspection helper — used by health checks and Swagger examples. */
  registeredChannels(): string[] {
    return [...this.strategies.keys()];
  }
}
