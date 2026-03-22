package io.squados.bus;

import io.squados.annotation.AgentRole;
import java.time.Instant;
import java.util.UUID;

/**
 * A message flowing through the AgentMessageBus.
 * Immutable — created once, read by all subscribers.
 */
public class AgentMessage {

    private final String      messageId;
    private final AgentRole   from;
    private final AgentRole   to;       // null = broadcast to all
    private final MessageType type;
    private final Object      payload;
    private final String      customType; // used when type == CUSTOM
    private final Instant     sentAt;

    public AgentMessage(AgentRole from, AgentRole to,
                        MessageType type, Object payload) {
        this.messageId  = UUID.randomUUID().toString().substring(0, 8);
        this.from       = from;
        this.to         = to;
        this.type       = type;
        this.payload    = payload;
        this.customType = null;
        this.sentAt     = Instant.now();
    }

    /** Broadcast constructor — no specific recipient */
    public AgentMessage(AgentRole from, MessageType type, Object payload) {
        this(from, null, type, payload);
    }

    /** Custom type constructor */
    public AgentMessage(AgentRole from, AgentRole to,
                        String customType, Object payload) {
        this.messageId  = UUID.randomUUID().toString().substring(0, 8);
        this.from       = from;
        this.to         = to;
        this.type       = MessageType.CUSTOM;
        this.payload    = payload;
        this.customType = customType;
        this.sentAt     = Instant.now();
    }

    public String      getMessageId()  { return messageId; }
    public AgentRole   getFrom()       { return from; }
    public AgentRole   getTo()         { return to; }
    public MessageType getType()       { return type; }
    public Object      getPayload()    { return payload; }
    public String      getCustomType() { return customType; }
    public Instant     getSentAt()     { return sentAt; }
    public boolean     isBroadcast()   { return to == null; }

    /** Typed payload accessor — throws if type doesn't match */
    @SuppressWarnings("unchecked")
    public <T> T getPayload(Class<T> cls) {
        if (payload == null) return null;
        if (!cls.isInstance(payload))
            throw new ClassCastException(
                "Payload is " + payload.getClass().getSimpleName()
                + ", not " + cls.getSimpleName());
        return (T) payload;
    }

    /** Topic key used for subscriber lookup */
    public String topic() {
        String typeStr = (type == MessageType.CUSTOM && customType != null)
            ? customType : type.name();
        return from.name() + "." + typeStr;
    }

    @Override
    public String toString() {
        return "AgentMessage{id='" + messageId + "', from=" + from
               + ", to=" + (to != null ? to : "*")
               + ", type=" + (customType != null ? customType : type)
               + ", payload=" + payload + "}";
    }
    private String nodeId = "local";

    public String toJson() {
        String p = payload == null ? "" : payload.toString()
            .replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"from\":\"" + from.name() + "\"," +
               "\"type\":\"" + type.name() + "\"," +
               "\"nodeId\":\"" + nodeId + "\"," +
               "\"payload\":\"" + p + "\"," +
               "\"ts\":\"" + messageId + "\"}";
    }

    public static AgentMessage fromJson(String json) {
        try {
            AgentRole   f = AgentRole.valueOf(extract(json,"from"));
            MessageType t = MessageType.valueOf(extract(json,"type"));
            String      p = extract(json,"payload");
            AgentMessage m = new AgentMessage(f,t,p);
            m.nodeId = extract(json,"nodeId");
            return m;
        } catch (Exception e) {
            return new AgentMessage(AgentRole.WILDCARD, MessageType.CUSTOM, json);
        }
    }

    private static String extract(String json, String key) {
        String s = "\"" + key + "\":\"";
        int i = json.indexOf(s); if (i<0) return "";
        i += s.length();
        int j = json.indexOf("\"",i); return j<0?"":json.substring(i,j);
    }
}

