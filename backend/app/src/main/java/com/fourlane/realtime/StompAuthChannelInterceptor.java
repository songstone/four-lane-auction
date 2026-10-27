package com.fourlane.realtime;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null || headers.getCommand() == null) {
            return message; // Heartbeat frames have no command.
        }
        switch (headers.getCommand()) {
            case CONNECT, STOMP -> {
                // Approved M0 restriction. Replace with JwtDecoder validation in M1-02 (ADR-003).
                if (headers.containsNativeHeader("Authorization")) {
                    throw new RejectedFrame(message, "TOKEN_INVALID");
                }
            }
            case SEND -> throw new RejectedFrame(message, "SEND_DENIED");
            case SUBSCRIBE -> {
                String destination = headers.getDestination();
                if (destination == null || !destination.matches("/topic/lanes/[0-9]+")) {
                    throw new RejectedFrame(message, "SUBSCRIPTION_DENIED");
                }
            }
            case UNSUBSCRIBE, DISCONNECT -> { /* No application commands. */ }
            default -> throw new RejectedFrame(message, "FRAME_DENIED");
        }
        return message;
    }

    static final class RejectedFrame extends MessageDeliveryException {
        private final String reason;

        RejectedFrame(Message<?> message, String reason) {
            super(message, reason);
            this.reason = reason;
        }

        String reason() {
            return reason;
        }
    }
}
