package io.squados.llm;

/**
 * A single turn in a multi-turn conversation.
 *
 * role    — "user" | "assistant" | "system"
 * content — The message text
 */
public record ConversationMessage(
        String role,
        String content
) {
    public static ConversationMessage user(String content)      { return new ConversationMessage("user", content); }
    public static ConversationMessage assistant(String content) { return new ConversationMessage("assistant", content); }
    public static ConversationMessage system(String content)    { return new ConversationMessage("system", content); }

    public boolean isUser()      { return "user".equals(role); }
    public boolean isAssistant() { return "assistant".equals(role); }
    public boolean isSystem()    { return "system".equals(role); }
}
