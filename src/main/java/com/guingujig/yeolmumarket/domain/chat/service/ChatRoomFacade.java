package com.guingujig.yeolmumarket.domain.chat.service;

import com.guingujig.yeolmumarket.domain.chat.dto.CreateChatRoomResponse;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.service.ProductService;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomFacade {

  private final UserService userService;
  private final ProductService productService;
  private final ChatRoomService chatRoomService;

  /**
   * 채팅방 생성에 필요한 회원·상품 조회를 각 도메인 Service로 조합한 뒤 채팅방 생성 책임을 위임한다.
   *
   * <p>상품 조회는 판매자 포함 pessimistic lock과 채팅 가능 검증을 보장한다.
   */
  @Transactional
  public CreateChatRoomResponse createChatRoom(Long buyerId, Long productId) {
    User buyer = userService.getExistingUser(buyerId);
    Product product = productService.getChatCreatableProductForUpdate(productId, buyerId);
    User seller = product.getSeller();

    return chatRoomService.findOrCreateChatRoom(buyer, product, seller);
  }
}
