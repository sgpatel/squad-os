export interface ActivityEvent {
  id: string
  timestamp: string
  type: 'AGENT_CALL' | 'APPROVAL' | 'VOTE' | 'SECURITY' | 'SYSTEM' | 'ERROR'
  agentName: string
  role: string | null
  message: string
  status: 'success' | 'error' | 'warning' | 'info'
  latencyMs: number | null
  tokens: number | null
}

export interface AgentSummary {
  name: string
  role: string
  description: string
  status: 'ACTIVE' | 'IDLE' | 'ERROR' | 'RATE_LIMITED'
  totalCalls: number
  successCalls: number
  errorCalls: number
  avgLatencyMs: number
  totalTokens: number
  totalPromptTokens: number
  totalCompletionTokens: number
  successRate: number
  lastCallAt: string | null
  annotations: string
}

export interface TimeSeriesPoint {
  label: string
  value: number
}

export interface AgentMetricEntry {
  name: string
  role: string
  calls: number
  errors: number
  tokens: number
  avgLatencyMs: number
}

export interface MetricsSummary {
  totalAgentCalls: number
  totalSuccessCalls: number
  totalErrorCalls: number
  overallSuccessRate: number
  totalTokensConsumed: number
  totalPromptTokens: number
  totalCompletionTokens: number
  avgLatencyMs: number
  p95LatencyMs: number
  p99LatencyMs: number
  perAgent: AgentMetricEntry[]
  callsOverTime: TimeSeriesPoint[]
  tokensOverTime: TimeSeriesPoint[]
  latencyOverTime: TimeSeriesPoint[]
  errorsByType: Record<string, number>
  activeRateLimits: number
  rateLimitBreach24h: number
}

export interface WorkflowItem {
  workflowId: string
  agentName: string
  state: 'PENDING' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'FAILED'
  createdAt: string
  updatedAt: string
  elapsedMs: number
  checkpointCount: number
  completedSteps: string[]
  lastStep: string | null
  error: string | null
}

export interface SecurityEvent {
  id: string
  timestamp: string
  type: string
  agentName: string
  user: string | null
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  message: string
  detail: string
}

export interface TraceSpan {
  traceId: string
  spanId: string
  spanName: string
  agentName: string
  role: string
  startTime: string
  endTime: string
  durationMs: number
  status: 'OK' | 'ERROR'
  errorMessage: string | null
  promptTokens: number
  completionTokens: number
  totalTokens: number
  inputLength: number
  outputLength: number
  attributes: Record<string, string>
}

export interface SystemHealth {
  status: 'UP' | 'DEGRADED' | 'DOWN'
  timestamp: string
  uptimeMs: number
  activeAgents: number
  totalAgents: number
  sseSubscribers: number
  jvmHeapUsedMb: number
  jvmHeapMaxMb: number
  heapUsagePct: number
  threadCount: number
  components: Record<string, string>
}
