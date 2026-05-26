/**
 * Strategy contract for notification channel delivery.
 *
 * Why Strategy here instead of a simple switch statement?
 *   A switch in the service couples every channel's implementation to a single
 *   class. Adding SMS requires opening NotificationsService and modifying it —
 *   a violation of the Open/Closed Principle. With a strategy per channel:
 *     - Each strategy is independently testable and replaceable.
 *     - The orchestrating service only knows the interface, never the concrete
 *       transport (SMTP, FCM, Twilio, etc.).
 *     - A new channel is added by writing one new strategy class and registering
 *       it in the NotificationStrategyRegistry — zero changes elsewhere.
 *
 * Design notes:
 *   - `channel` is a discriminator so the registry can look up the right strategy
 *     without conditional logic on the caller's side.
 *   - `send` is async because every real transport involves network I/O. Returning
 *     a Promise allows the caller to await, collect results, or fan-out to multiple
 *     channels without blocking the event loop.
 */
export interface NotifyPayload {
  /** Stable identifier for the recipient (user ID, email address, device token). */
  recipientId: string;

  /** Subject line or notification title (≤ 60 chars for push compatibility). */
  subject: string;

  /** Plain-text notification body — no HTML or markdown so it works cross-channel. */
  body: string;

  /** Arbitrary metadata the strategy may use (e.g. deep-link URL for push). */
  metadata?: Record<string, unknown>;
}

export interface NotifyResult {
  /** Channel that processed the delivery. */
  channel: string;

  /** Provider-assigned message identifier for deduplication and tracing. */
  messageId: string;

  /** Delivery outcome: queued = accepted by provider, not yet delivered. */
  status: 'sent' | 'queued' | 'failed';

  /** ISO-8601 timestamp when the strategy accepted the message. */
  dispatchedAt: string;
}

export interface NotificationStrategy {
  /** Matches the 'channel' field on incoming NotifyRequest. */
  readonly channel: 'email' | 'push' | 'sms';

  /**
   * Deliver a notification via this strategy's transport.
   *
   * Implementations MUST NOT throw on transient provider errors — they should
   * return status='failed' with a meaningful messageId so the caller can log and
   * retry. They MAY throw on programming errors (null payload, missing config).
   */
  send(payload: NotifyPayload): Promise<NotifyResult>;
}

/** Injection token so the registry can receive the full strategy list from DI. */
export const NOTIFICATION_STRATEGIES = Symbol('NOTIFICATION_STRATEGIES');
