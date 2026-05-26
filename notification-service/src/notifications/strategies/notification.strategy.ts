/**
 * Contract every notification channel strategy must fulfil.
 *
 * Why Strategy Pattern instead of switch(channel)?
 *   A switch statement in the service couples every channel's transport to a
 *   single class. Adding a new channel (e.g. Slack) means opening and modifying
 *   that class — a violation of the Open/Closed Principle.
 *   With this interface:
 *     - Each channel is an independent, testable unit.
 *     - NotificationsController and NotificationChannelRegistry never change
 *       when a new channel is registered.
 *     - See SlackStrategy for a concrete OCP demonstration.
 *
 * Signature: execute(userId, message) — intentionally minimal.
 *   The endpoint spec is { userId, message, channel }. Keeping the interface
 *   thin means strategies don't need to destructure a payload object, and the
 *   contract is immediately readable.
 */
export interface NotificationStrategy {
  /** Discriminator used by the registry to select this strategy at runtime. */
  readonly channel: string;

  /**
   * Deliver `message` to the user identified by `userId` via this transport.
   *
   * Convention:
   *   - Returns void — the caller only needs to know that dispatch was attempted.
   *   - Implementations SHOULD log failures and resolve (not reject) on transient
   *     errors so the controller can always return a clean response.
   *   - Implementations MAY throw on programming errors (bad config, null args).
   */
  execute(userId: string, message: string): Promise<void>;
}

/** NestJS multi-provider token — the registry receives all strategies as an array. */
export const NOTIFICATION_STRATEGIES = Symbol('NOTIFICATION_STRATEGIES');
