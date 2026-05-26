import { Controller, Get } from '@nestjs/common';
import { ApiOperation, ApiResponse, ApiTags } from '@nestjs/swagger';
import {
  HealthCheck,
  HealthCheckResult,
  HealthCheckService,
  MemoryHealthIndicator,
} from '@nestjs/terminus';

/**
 * Health endpoint consumed by Docker HEALTHCHECK and any load balancer.
 *
 * @nestjs/terminus provides built-in indicators for memory, disk, database, HTTP
 * endpoints, etc. MemoryHealthIndicator checks the heap size — an early warning
 * for memory leaks without requiring a full database connection check at this tier.
 *
 * The Spring Boot backend's /actuator/health is the authoritative health gate for
 * the data layer. This service only needs to verify its own process health.
 */
@ApiTags('health')
@Controller('health')
export class HealthController {
  constructor(
    private readonly health: HealthCheckService,
    private readonly memory: MemoryHealthIndicator,
  ) {}

  @Get()
  @HealthCheck()
  @ApiOperation({ summary: 'Service health check' })
  @ApiResponse({ status: 200, description: 'Service is healthy' })
  @ApiResponse({ status: 503, description: 'Service is unhealthy' })
  check(): Promise<HealthCheckResult> {
    return this.health.check([
      // Alert if heap exceeds 300 MB — this service should run comfortably under 150 MB.
      () => this.memory.checkHeap('memory_heap', 300 * 1024 * 1024),
    ]);
  }
}
