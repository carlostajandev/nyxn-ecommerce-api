import { Inject, Injectable, Logger, NotFoundException } from '@nestjs/common';
import {
  NOTIFICATION_STRATEGIES,
  NotificationStrategy,
} from './strategies/notification.strategy';

/**
 * Registry that maps a channel name to its delivery strategy.
 *
 * Why a registry instead of direct injection of each strategy?
 *   Direct injection means the orchestrating service must declare a constructor
 *   parameter for every strategy — when a new channel is added, the service
 *   must be modified. A registry inverts that: each strategy registers itself;
 *   the service only ever calls registry.get(channel). Adding SMS requires
 *   only registering SmsNotificationStrategy with the module.
 *
 * How strategies are wired in:
 *   The NestJS module provides all strategies under the NOTIFICATION_STRATEGIES
 *   multi-provider token:
 *
 *     { provide: NOTIFICATION_STRATEGIES, useClass: EmailNotificationStrategy, multi: true }
 *     { provide: NOTIFICATION_STRATEGIES, useClass: PushNotificationStrategy,  multi: true }
 *     { provide: NOTIFICATION_STRATEGIES, useClass: SmsNotificationStrategy,   multi: true }
 *
 *   NestJS injects the entire array here. The registry builds a Map on construction
 *   so lookup is O(1) regardless of how many strategies exist.
 */
@Injectable()
export class NotificationStrategyRegistry {
  private readonly logger = new Logger(NotificationStrategyRegistry.name);
  private readonly registry = new Map<string, NotificationStrategy>();

  constructor(
    @Inject(NOTIFICATION_STRATEGIES)
    strategies: NotificationStrategy[],
  ) {
    for (const strategy of strategies) {
      this.registry.set(strategy.channel, strategy);
      this.logger.log(`Registered notification strategy: ${strategy.channel}`);
    }
  }

  /**
   * Returns the strategy for the given channel.
   *
   * Throws NotFoundException (HTTP 404) rather than returning null so that the
   * caller does not need to null-check and can propagate the error directly to
   * the HTTP layer via NestJS's built-in exception filter.
   */
  get(channel: string): NotificationStrategy {
    const strategy = this.registry.get(channel);

    if (!strategy) {
      const available = [...this.registry.keys()].join(', ');
      throw new NotFoundException(
        `No notification strategy registered for channel '${channel}'. ` +
          `Available channels: ${available}`,
      );
    }

    return strategy;
  }

  /** Returns all registered channel names — used for Swagger docs and health checks. */
  availableChannels(): string[] {
    return [...this.registry.keys()];
  }
}
