package io.squados.annotation;

import io.squados.bus.MessageType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a subscriber to AgentMessageBus events.
 *
 * The method fires whenever an AgentMessage matching the
 * from + type combination is published to the bus.
 *
 * Use AgentRole.WILDCARD as from to subscribe to all senders.
 *
 * Example — NurseBot reacts to IronVeil's HP events:
 * <pre>
 * {@literal @}OnMessage(from = AgentRole.TANK, type = MessageType.HP_CRITICAL)
 * public void emergencyHeal(AgentMessage msg) {
 *     int hp = msg.getPayload(Integer.class);
 *     deployHeal(hp);
 * }
 * </pre>
 *
 * Example — Oracle listens to every agent's status:
 * <pre>
 * {@literal @}OnMessage(from = AgentRole.WILDCARD, type = MessageType.STATUS_UPDATE)
 * public void updateBattlePicture(AgentMessage msg) { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnMessage {
    AgentRole   from();
    MessageType type();
}
