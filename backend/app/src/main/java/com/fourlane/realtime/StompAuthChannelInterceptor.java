package com.fourlane.realtime;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {
    private final JwtDecoder decoder;
    private final JwtAuthenticationConverter converter;

    public StompAuthChannelInterceptor(JwtDecoder decoder, JwtAuthenticationConverter converter) {
        this.decoder = decoder;
        this.converter = converter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null || headers.getCommand() == null) {
            return message; // Heartbeat frames have no command.
        }
        switch (headers.getCommand()) {
            case CONNECT, STOMP -> authenticate(message, headers);
            case SEND -> throw new RejectedFrame(message, "SEND_DENIED");
            case SUBSCRIBE -> {
                String destination = headers.getDestination();
                boolean lane = destination != null && destination.matches("/topic/lanes/[0-9]+");
                boolean personal = "/user/queue/notifications".equals(destination)
                        && headers.getUser() instanceof Authentication auth && auth.isAuthenticated();
                if (!lane && !personal) {
                    throw new RejectedFrame(message, "SUBSCRIPTION_DENIED");
                }
            }
            case UNSUBSCRIBE, DISCONNECT -> { /* No application commands. */ }
            default -> throw new RejectedFrame(message, "FRAME_DENIED");
        }
        return message;
    }

    private void authenticate(Message<?> message, StompHeaderAccessor headers) {
        // CONNECT alone determines identity, including when the HTTP upgrade had a Principal.
        headers.setUser(null);
        var values = headers.getNativeHeader("Authorization");
        if (values == null) return;
        headers.removeNativeHeader("Authorization"); // Do not retain credentials in broker headers.
        if (values.size() != 1 || values.getFirst() == null
                || !values.getFirst().matches("Bearer [A-Za-z0-9_.-]+")) {
            throw new RejectedFrame(message, "TOKEN_INVALID");
        }
        try {
            headers.setUser(converter.convert(decoder.decode(values.getFirst().substring(7))));
        } catch (JwtValidationException invalid) {
            boolean expired = invalid.getErrors().stream()
                    .anyMatch(error -> "TOKEN_EXPIRED".equals(error.getDescription()));
            throw new RejectedFrame(message, expired ? "TOKEN_EXPIRED" : "TOKEN_INVALID");
        } catch (JwtException | IllegalArgumentException invalid) {
            throw new RejectedFrame(message, "TOKEN_INVALID");
        }
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
