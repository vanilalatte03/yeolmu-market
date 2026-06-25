package com.guingujig.yeolmumarket.domain.chat.websocket;

import com.guingujig.yeolmumarket.domain.chat.service.ChatRoomAuthorizationService;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.security.AuthenticatedUser;
import java.security.Principal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 채팅방 구독 요청이 실제 참여자의 방인지 검증한다.
 *
 * <p>구독 실패는 WebSocket 연결을 끊지 않고 사용자 오류 큐로 알려야 하므로 REST 예외 처리 대신 inbound channel에서 차단한다.
 */
@Component
@RequiredArgsConstructor
public class ChatSubscriptionAuthorizationInterceptor implements ChannelInterceptor {

  private static final Pattern CHAT_ROOM_SUBSCRIPTION_PATTERN =
      Pattern.compile("^/sub/chat-rooms/(\\d+)$");

  private final ChatRoomAuthorizationService chatRoomAuthorizationService;
  private final ChatWebSocketErrorSender chatWebSocketErrorSender;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = getAccessor(message);
    if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
      return message;
    }

    Optional<Long> roomId = extractChatRoomId(accessor.getDestination());
    if (roomId.isEmpty()) {
      return message;
    }

    Principal user = requireUser(accessor);
    Long userId = authenticatedUser(user).userId();
    try {
      chatRoomAuthorizationService.validateParticipant(roomId.get(), userId);
      return message;
    } catch (BusinessException exception) {
      if (isChatRoomAuthorizationError(exception.getErrorCode())) {
        chatWebSocketErrorSender.sendToUser(user, exception.getErrorCode(), roomId.get());
        // null을 반환하면 브로커까지 메시지가 전달되지 않아 권한 없는 구독이 등록되지 않는다.
        return null;
      }
      throw exception;
    }
  }

  private Optional<Long> extractChatRoomId(String destination) {
    if (destination == null) {
      return Optional.empty();
    }
    Matcher matcher = CHAT_ROOM_SUBSCRIPTION_PATTERN.matcher(destination);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    return Optional.of(Long.parseLong(matcher.group(1)));
  }

  private Principal requireUser(StompHeaderAccessor accessor) {
    Principal user = accessor.getUser();
    if (user == null) {
      throw new ChatWebSocketAuthenticationException(ErrorCode.UNAUTHORIZED);
    }
    return user;
  }

  private AuthenticatedUser authenticatedUser(Principal user) {
    if (user instanceof Authentication authentication
        && authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser) {
      return authenticatedUser;
    }
    throw new ChatWebSocketAuthenticationException(ErrorCode.UNAUTHORIZED);
  }

  private boolean isChatRoomAuthorizationError(ErrorCode errorCode) {
    return errorCode == ErrorCode.CHAT_ROOM_NOT_FOUND
        || errorCode == ErrorCode.CHAT_ROOM_ACCESS_DENIED;
  }

  private StompHeaderAccessor getAccessor(Message<?> message) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    return accessor != null ? accessor : StompHeaderAccessor.wrap(message);
  }
}
