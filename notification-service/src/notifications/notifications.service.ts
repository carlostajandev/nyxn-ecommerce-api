import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Message, PubSub, Subscription } from '@google-cloud/pubsub';
import { ProductEventDto } from './dto/product-event.dto';

/**
 * Pub/Sub subscriber for product domain events published by the Spring Boot backend.
 *
 * Lifecycle:
 *   OnModuleInit  → opens the subscription and starts receiving messages.
 *   OnModuleDestroy → closes the subscription gracefully so in-flight messages
 *                     are acked/nacked before the gRPC channel closes. A hard
 *                     shutdown (SIGKILL) would leave in-flight messages un-acked;
 *                     GCP would redeliver them after the ack deadline expires.
 *
 * Ack / Nack contract:
 *   - Ack on successful processing: removes the message from the subscription.
 *   - Nack on any error: triggers redelivery. After maxDeliveryAttempts (5,
 *     configured in docker-compose pubsub-init), GCP routes the message to the
 *     dead-letter topic (product-events-dlq). From there an operator can inspect
 *     the message and replay or discard it.
 *   - Unknown event types are ACKED — retrying them would not change the outcome
 *     (it is a producer schema bug) and would spin the message into the DLQ
 *     unnecessarily, causing alert fatigue.
 *
 * Flow control:
 *   maxMessages: 10 — limits concurrent in-flight handlers so the process stays
 *   responsive under burst load. The Pub/Sub client library automatically back-
 *   pressures the subscription when this limit is reached.
 */
@Injectable()
export class NotificationsService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(NotificationsService.name);
  private subscription!: Subscription;

  constructor(
    private readonly pubSub: PubSub,
    private readonly config: ConfigService,
  ) {}

  onModuleInit(): void {
    const subscriptionName = this.config.get<string>(
      'PUBSUB_SUBSCRIPTION_PRODUCT_EVENTS',
      'product-events-subscription',
    );

    this.subscription = this.pubSub.subscription(subscriptionName, {
      flowControl: {
        // Allow at most 10 messages in-flight at once. Prevents memory spikes
        // during burst traffic and keeps handler latency predictable.
        maxMessages: 10,
      },
    });

    this.subscription.on('message', (message: Message) => this.handleMessage(message));
    this.subscription.on('error', (error: Error) => {
      // Log and continue — a subscription error (gRPC connection drop, etc.) does not
      // require application restart. The client library reconnects automatically.
      this.logger.error('Pub/Sub subscription error', error.stack);
    });

    this.logger.log(`Subscribed to '${subscriptionName}'`);
  }

  onModuleDestroy(): void {
    // close() initiates a graceful shutdown: stops pulling new messages, waits for
    // in-flight handlers to complete, then closes the gRPC stream.
    this.subscription.close().catch((err: Error) => {
      this.logger.warn('Error during subscription close', err.message);
    });
  }

  // ─── Private message handler ───────────────────────────────────────────────

  private handleMessage(message: Message): void {
    const messageId = message.id;

    try {
      const raw = message.data.toString('utf-8');
      const event: ProductEventDto = JSON.parse(raw) as ProductEventDto;

      this.dispatchEvent(event, messageId);
      message.ack();
      this.logger.debug(`Message ${messageId} acked (type=${event.eventType})`);
    } catch (error) {
      // JSON parse error, unknown structure, or handler exception.
      // Nack to trigger redelivery → eventual DLQ routing by GCP.
      message.nack();
      this.logger.error(
        `Message ${messageId} nacked — will retry or route to DLQ`,
        (error as Error).stack,
      );
    }
  }

  private dispatchEvent(event: ProductEventDto, messageId: string): void {
    switch (event.eventType) {
      case 'PRODUCT_CREATED':
        this.onProductCreated(event, messageId);
        break;
      case 'PRODUCT_UPDATED':
        this.onProductUpdated(event, messageId);
        break;
      case 'PRODUCT_DELETED':
        this.onProductDeleted(event, messageId);
        break;
      default:
        // Ack unknown types to prevent DLQ spin. Log a warning for observability.
        this.logger.warn(
          `Unknown eventType '${event.eventType}' in message ${messageId} — acking to prevent DLQ loop`,
        );
    }
  }

  // ─── Event handlers ────────────────────────────────────────────────────────
  // Each handler is a separate method so it can be independently tested
  // and extended (e.g. push to FCM, send to SMTP relay) without touching the
  // subscription plumbing above.

  private onProductCreated(event: ProductEventDto, messageId: string): void {
    this.logger.log(
      `[PRODUCT_CREATED] entityId=${event.entityId} messageId=${messageId}`,
    );
    // Extension point: send "new product available" push notification or email here.
  }

  private onProductUpdated(event: ProductEventDto, messageId: string): void {
    this.logger.log(
      `[PRODUCT_UPDATED] entityId=${event.entityId} messageId=${messageId}`,
    );
    // Extension point: notify users who have this product wishlisted.
  }

  private onProductDeleted(event: ProductEventDto, messageId: string): void {
    this.logger.log(
      `[PRODUCT_DELETED] entityId=${event.entityId} messageId=${messageId}`,
    );
    // Extension point: notify users with this product in their cart.
  }
}
