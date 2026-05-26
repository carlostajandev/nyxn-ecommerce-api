import { Module } from '@nestjs/common';
import { ClaudeAgentService } from './claude-agent.service';
import { AgentController } from './agent.controller';

@Module({
  controllers: [AgentController],
  providers: [ClaudeAgentService],
  exports: [ClaudeAgentService],
})
export class AgentModule {}
