import { NestFactory } from '@nestjs/core';
import { ValidationPipe } from '@nestjs/common';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { AppModule } from './app.module';

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create(AppModule);

  // Global validation pipe: any DTO annotated with class-validator decorators
  // is automatically validated before reaching the controller. Unknown properties
  // are stripped (whitelist) and an exception is thrown if non-whitelisted properties
  // are explicitly forbidden (forbidNonWhitelisted). This prevents mass-assignment bugs.
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
    }),
  );

  // OpenAPI documentation — available at /api-docs in development.
  const config = new DocumentBuilder()
    .setTitle('NYXN Notification Service')
    .setDescription(
      'NestJS microservice for product and order event notifications. ' +
        'Consumes GCP Pub/Sub events from the Spring Boot backend and optionally ' +
        'generates AI-powered notification content using Claude.',
    )
    .setVersion('1.0')
    .addTag('notifications', 'Event-driven notification processing')
    .addTag('agent', 'Claude AI-powered smart notifications')
    .addTag('health', 'Service health checks')
    .build();
  const document = SwaggerModule.createDocument(app, config);
  SwaggerModule.setup('api-docs', app, document);

  const port = process.env.PORT ?? 3000;
  await app.listen(port);

  console.log(`Notification service running on port ${port}`);
  console.log(`API docs: http://localhost:${port}/api-docs`);
}

bootstrap();
