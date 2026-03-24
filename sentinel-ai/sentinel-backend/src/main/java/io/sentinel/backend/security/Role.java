package io.sentinel.backend.security;
public enum Role {
  ADMIN,        // full access
  ANALYST,      // read + analyse
  REVIEWER,     // approve/reject replies
  READ_ONLY     // dashboard view only
}