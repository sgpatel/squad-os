export type SentimentLabel = "POSITIVE" | "NEGATIVE" | "NEUTRAL";
export type UrgencyLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type Priority = "P1" | "P2" | "P3" | "P4";
export type ReplyStatus = "PENDING" | "APPROVED" | "POSTED" | "REJECTED" | "SKIPPED";
export type ProcessingStatus = "NEW" | "ANALYSING" | "DONE" | "ERROR";

export interface Mention {
  id: string;
  platform: string;
  handle: string;
  authorUsername: string;
  authorName: string;
  authorFollowers: number;
  text: string;
  language: string;
  postedAt: string;
  url?: string;
  likeCount: number;
  retweetCount: number;
  sentimentLabel?: SentimentLabel;
  sentimentScore?: number;
  primaryEmotion?: string;
  urgency?: UrgencyLevel;
  topic?: string;
  summary?: string;
  priority?: Priority;
  escalationPath?: string;
  assignedTeam?: string;
  ticketId?: string;
  ticketSystem?: string;
  ticketStatus?: string;
  replyText?: string;
  replyStatus?: ReplyStatus;
  processingStatus: ProcessingStatus;
  urgencyScore?: number;
  viralRiskScore?: number;
  isViral?: boolean;
}

export interface AnalyticsSummary {
  totalMentions: number;
  positiveMentions: number;
  negativeMentions: number;
  neutralMentions: number;
  brandHealthScore: number;
  criticalAlerts: number;
  pendingReplies: number;
  openTickets: number;
  resolvedTickets: number;
  avgSentimentScore: number;
}

export interface Ticket {
  id: string;
  title: string;
  status: string;
  priority: string;
  team: string;
  mentionId: string;
  mentionText: string;
  resolution?: string;
  createdAt: number;
}

export interface TrendPoint {
  hour: number;
  positive: number;
  negative: number;
  neutral: number;
  total: number;
}