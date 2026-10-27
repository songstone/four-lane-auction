package com.fourlane.realtime;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

public class StompErrorHandler extends StompSubProtocolErrorHandler {
    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable exception) {
        String reason = "FRAME_DENIED";
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof StompAuthChannelInterceptor.RejectedFrame rejected) {
                reason = rejected.reason();
                break;
            }
        }
        var headers = StompHeaderAccessor.create(StompCommand.ERROR);
        headers.setMessage(reason);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
