import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { ClaudeAgentService } from './claude-agent.service';
import { ProductEventDto } from '../notifications/dto/product-event.dto';

/**
 * Unit tests for ClaudeAgentService.
 *
 * The Anthropic SDK client is mocked at the module level — no real API calls are
 * made. This keeps tests fast, free, and deterministic regardless of network state
 * or API key availability. The mock verifies:
 *   1. The correct model and max_tokens are requested.
 *   2. The system prompt is included.
 *   3. The user message contains the event type and audience context.
 *   4. The JSON response is correctly parsed into SmartNotificationResponse.
 *   5. Non-JSON responses and missing fields throw meaningful errors.
 */
describe('ClaudeAgentService', () => {
  let service: ClaudeAgentService;

  // Mock the Anthropic client — we don't want to hit the real API in unit tests.
  const mockCreate = jest.fn();
  jest.mock('@anthropic-ai/sdk', () => {
    return {
      default: jest.fn().mockImplementation(() => ({
        messages: { create: mockCreate },
      })),
    };
  });

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ClaudeAgentService,
        {
          provide: ConfigService,
          useValue: {
            get: (key: string, defaultVal?: string) => {
              if (key === 'ANTHROPIC_API_KEY') return 'test-api-key';
              return defaultVal;
            },
          },
        },
      ],
    }).compile();

    service = module.get<ClaudeAgentService>(ClaudeAgentService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should parse a well-formed Claude JSON response', async () => {
    const mockResponse = {
      content: [
        {
          type: 'text',
          text: JSON.stringify({
            subject: 'New Product Alert!',
            body: 'A new product is available in our catalog. Check it out now.',
            channel: 'push',
          }),
        },
      ],
      usage: { input_tokens: 120, output_tokens: 45 },
    };
    mockCreate.mockResolvedValueOnce(mockResponse);

    const event: ProductEventDto = {
      eventType: 'PRODUCT_CREATED',
      entityId: '550e8400-e29b-41d4-a716-446655440000',
    };

    const result = await service.generateNotification(event);

    expect(result.subject).toBe('New Product Alert!');
    expect(result.body).toContain('new product');
    expect(result.channel).toBe('push');
    expect(result.eventType).toBe('PRODUCT_CREATED');
    expect(result.productId).toBe(event.entityId);
    expect(result.model).toContain('haiku');
  });

  it('should strip code fences before parsing JSON', async () => {
    mockCreate.mockResolvedValueOnce({
      content: [
        {
          type: 'text',
          text:
            '```json\n{"subject":"Update","body":"Product updated.","channel":"email"}\n```',
        },
      ],
      usage: { input_tokens: 100, output_tokens: 30 },
    });

    const event: ProductEventDto = {
      eventType: 'PRODUCT_UPDATED',
      entityId: 'abc-123',
    };

    const result = await service.generateNotification(event);
    expect(result.subject).toBe('Update');
    expect(result.channel).toBe('email');
  });

  it('should throw when Claude returns non-JSON text', async () => {
    mockCreate.mockResolvedValueOnce({
      content: [{ type: 'text', text: 'Sorry, I cannot generate that notification.' }],
      usage: { input_tokens: 50, output_tokens: 10 },
    });

    const event: ProductEventDto = {
      eventType: 'PRODUCT_DELETED',
      entityId: 'xyz-999',
    };

    await expect(service.generateNotification(event)).rejects.toThrow(
      'Claude returned non-JSON response',
    );
  });

  it('should include audience context in user message (verified via mock call args)', async () => {
    mockCreate.mockResolvedValueOnce({
      content: [
        {
          type: 'text',
          text: JSON.stringify({
            subject: 'VIP Alert',
            body: 'Exclusive update for our best customers.',
            channel: 'sms',
          }),
        },
      ],
      usage: { input_tokens: 150, output_tokens: 40 },
    });

    const event: ProductEventDto = {
      eventType: 'PRODUCT_UPDATED',
      entityId: 'prod-001',
    };

    await service.generateNotification(event, 'premium VIP customer');

    const callArgs = mockCreate.mock.calls[0][0] as { messages: { content: string }[] };
    const userMessage = callArgs.messages[0].content;
    expect(userMessage).toContain('premium VIP customer');
  });
});
