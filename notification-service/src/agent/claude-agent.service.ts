import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Anthropic from '@anthropic-ai/sdk';
import { ProductEventDto, SmartNotificationResponse } from '../notifications/dto/product-event.dto';

/**
 * Claude AI agent for generating personalised notification content.
 *
 * Section 6B — Claude agent integration.
 *
 * Why an agent instead of a simple completion?
 *   A single-turn completion would work for simple templates. An agent with
 *   tool-calling allows Claude to reason about the notification context, potentially
 *   calling tools to look up product details, customer preferences, or A/B test
 *   variants. Even in this demonstration without external tool calls, the system
 *   prompt establishes the agent's persona and constraints so that future tool
 *   additions require only adding the tool definition, not rewriting the logic.
 *
 * Model choice:
 *   claude-haiku-4-5 — fastest and cheapest Haiku model. Notification content
 *   generation is a high-volume, latency-sensitive task where speed and cost matter
 *   more than the depth of reasoning that Sonnet/Opus provide. The structured
 *   system prompt constrains the output format so model intelligence is not the
 *   bottleneck.
 *
 * Output format:
 *   The system prompt instructs Claude to respond with a JSON object. We parse that
 *   JSON instead of hoping Claude produces clean prose — structured output is more
 *   reliable for downstream consumers than free-form text.
 */
@Injectable()
export class ClaudeAgentService {
  private readonly logger = new Logger(ClaudeAgentService.name);
  private readonly client: Anthropic;
  private readonly model = 'claude-haiku-4-5-20251001';

  // System prompt is a constant: it defines the agent's role, output schema, and
  // hard constraints. Keeping it here (not in a database or config file) makes it
  // version-controlled and diff-able alongside the code that depends on it.
  private static readonly SYSTEM_PROMPT = `You are the notification content writer for NYXN, a premium e-commerce platform.
Your job is to generate concise, compelling notification messages for product events.

Always respond with a valid JSON object matching this exact schema:
{
  "subject": "<60 character subject line>",
  "body": "<2-3 sentence notification body, plain text, no markdown>",
  "channel": "<one of: email | push | sms>"
}

Rules:
- subject must be ≤ 60 characters (counts against push notification limits)
- body must be plain text — no markdown, no HTML — to work across all channels
- channel recommendation: use 'push' for time-sensitive events, 'email' for informational, 'sms' for urgent/critical
- Tone: friendly, professional, slightly urgent but never alarmist
- Do not include product IDs, UUIDs, or technical identifiers in the output
- Never fabricate prices or stock numbers unless provided in the context`;

  constructor(private readonly config: ConfigService) {
    const apiKey = this.config.get<string>('ANTHROPIC_API_KEY');

    if (!apiKey) {
      this.logger.warn(
        'ANTHROPIC_API_KEY is not set — Claude agent will throw on first call. ' +
          'Set the env var or use a mock in tests.',
      );
    }

    // The Anthropic SDK reads ANTHROPIC_API_KEY from the environment automatically
    // if not provided explicitly. Passing it explicitly here makes the dependency
    // clear and allows per-request key overrides in testing.
    this.client = new Anthropic({ apiKey: apiKey ?? 'not-set' });
  }

  /**
   * Generates a smart notification for a product event using Claude.
   *
   * The user message provides the event context. Claude reasons about the most
   * appropriate subject, body, and delivery channel based on the system prompt
   * constraints and the event type.
   *
   * @param event    The product domain event triggering the notification.
   * @param audience Optional audience context for personalisation.
   * @returns        Structured notification content ready for delivery.
   */
  async generateNotification(
    event: ProductEventDto,
    audience: string = 'general customer',
  ): Promise<SmartNotificationResponse> {
    const userMessage = this.buildUserMessage(event, audience);

    this.logger.debug(
      `Calling Claude ${this.model} for event ${event.eventType} / product ${event.entityId}`,
    );

    const startMs = Date.now();
    const response = await this.client.messages.create({
      model: this.model,
      max_tokens: 256,
      system: ClaudeAgentService.SYSTEM_PROMPT,
      messages: [{ role: 'user', content: userMessage }],
    });

    const latencyMs = Date.now() - startMs;
    this.logger.log(
      `Claude response received in ${latencyMs}ms — ` +
        `input_tokens=${response.usage.input_tokens} output_tokens=${response.usage.output_tokens}`,
    );

    // Extract text from the first content block. Claude always returns at least one
    // text block when the model is prompted for JSON without tool use.
    const firstBlock = response.content[0];
    if (firstBlock.type !== 'text') {
      throw new Error(`Unexpected content type from Claude: ${firstBlock.type}`);
    }

    const parsed = this.parseClaudeResponse(firstBlock.text);

    return {
      eventType: event.eventType,
      productId: event.entityId,
      subject: parsed.subject,
      body: parsed.body,
      channel: parsed.channel,
      model: this.model,
    };
  }

  // ─── Private helpers ───────────────────────────────────────────────────────

  private buildUserMessage(event: ProductEventDto, audience: string): string {
    const eventDescriptions: Record<string, string> = {
      PRODUCT_CREATED: 'A new product has just been added to the catalog.',
      PRODUCT_UPDATED: 'An existing product has been updated (price, description, or stock).',
      PRODUCT_DELETED: 'A product has been removed from the catalog.',
    };

    const description = eventDescriptions[event.eventType] ?? `Event type: ${event.eventType}`;
    const payloadContext =
      event.payload ? `\nProduct details: ${JSON.stringify(event.payload)}` : '';

    return (
      `Event: ${event.eventType}\n` +
      `${description}${payloadContext}\n` +
      `Target audience: ${audience}\n\n` +
      `Generate an appropriate notification for this event.`
    );
  }

  private parseClaudeResponse(text: string): {
    subject: string;
    body: string;
    channel: string;
  } {
    // Claude may wrap the JSON in a code fence. Strip it before parsing.
    const cleaned = text.replace(/```json\n?/g, '').replace(/```\n?/g, '').trim();

    try {
      const parsed = JSON.parse(cleaned) as {
        subject: string;
        body: string;
        channel: string;
      };

      if (!parsed.subject || !parsed.body || !parsed.channel) {
        throw new Error('Claude response missing required fields: subject, body, channel');
      }

      return parsed;
    } catch (error) {
      this.logger.error(`Failed to parse Claude JSON response: ${text}`, (error as Error).message);
      throw new Error(`Claude returned non-JSON response: ${text.substring(0, 100)}...`);
    }
  }
}
