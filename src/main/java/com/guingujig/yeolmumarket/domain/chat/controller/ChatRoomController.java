package com.guingujig.yeolmumarket.domain.chat.controller;

import com.guingujig.yeolmumarket.domain.chat.dto.ChatMessagesResponse;
import com.guingujig.yeolmumarket.domain.chat.dto.ChatRoomListItemResponse;
import com.guingujig.yeolmumarket.domain.chat.dto.CreateChatRoomResponse;
import com.guingujig.yeolmumarket.domain.chat.service.ChatRoomService;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import com.guingujig.yeolmumarket.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ChatRoomController {

  private final ChatRoomService chatRoomService;

  @PostMapping("/products/{productId}/chat-rooms")
  public ResponseEntity<ApiResponse<CreateChatRoomResponse>> createChatRoom(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long productId) {
    CreateChatRoomResponse response =
        chatRoomService.createChatRoom(authenticatedUser.userId(), productId);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @GetMapping("/chat-rooms")
  public ResponseEntity<ApiResponse<PageResponse<ChatRoomListItemResponse>>> getMyChatRooms(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    PageResponse<ChatRoomListItemResponse> response =
        chatRoomService.getMyChatRooms(authenticatedUser.userId(), page, size);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/chat-rooms/{roomId}/messages")
  public ResponseEntity<ApiResponse<ChatMessagesResponse>> getPreviousMessages(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @PathVariable Long roomId,
      @RequestParam(required = false) Long beforeMessageId,
      @RequestParam(defaultValue = "30") int size) {
    ChatMessagesResponse response =
        chatRoomService.getPreviousMessages(
            authenticatedUser.userId(), roomId, beforeMessageId, size);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
