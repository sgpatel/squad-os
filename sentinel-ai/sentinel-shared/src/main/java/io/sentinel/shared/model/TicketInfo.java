package io.sentinel.shared.model;
import java.time.Instant;
public class TicketInfo {
    public String   ticketId;
    public String   externalId;   // Zendesk/Jira ID
    public String   system;       // ZENDESK / JIRA / FRESHDESK / SALESFORCE
    public String   status;       // OPEN / IN_PROGRESS / RESOLVED / CLOSED
    public String   priority;     // P1 / P2 / P3 / P4
    public String   assignee;
    public String   team;
    public Instant  createdAt;
    public Instant  updatedAt;
    public Instant  slaDeadline;
    public boolean  slaBreach;
    public String   url;          // link to ticket in CRM
    public String   resolution;
}