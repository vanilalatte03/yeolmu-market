package com.guingujig.yeolmumarket.domain.chat.service;

import com.guingujig.yeolmumarket.domain.chat.dto.CreateChatRoomResponse;
import com.guingujig.yeolmumarket.domain.chat.entity.ChatRoom;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatRoomRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

  private final ChatRoomRepository chatRoomRepository;
  private final ProductRepository productRepository;
  private final UserRepository userRepository;

  /**
   * 구매자와 상품 판매자 사이의 채팅방을 생성하거나 기존 방을 반환한다.
   *
   * <p>동시 요청으로 유니크 제약 충돌이 발생하면, 이미 생성된 채팅방을 다시 조회해 같은 결과로 수렴시킨다.
   *
   * <p>메서드 전체에 트랜잭션을 두지 않는다. 충돌 후 재조회를 새 트랜잭션(새 스냅샷)으로 수행해야 동시 커밋된 행을 확실히 보기 때문이다. 한 트랜잭션으로 묶으면
   * INSERT 실패가 트랜잭션을 rollback-only로 만들고, MySQL 기본 격리수준(REPEATABLE READ)의 스냅샷 탓에 재조회가 빈 결과를 볼 수 있다.
   */
  public CreateChatRoomResponse createChatRoom(Long buyerId, Long productId) {
    User buyer =
        userRepository
            .findById(buyerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    Product product =
        productRepository
            .findWithSellerById(productId)
            .filter(this::isCreatableChatProduct)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    User seller = product.getSeller();

    if (seller.getId().equals(buyer.getId())) {
      throw new BusinessException(ErrorCode.CANNOT_CHAT_OWN_PRODUCT);
    }

    return CreateChatRoomResponse.from(findOrCreateChatRoom(product, buyer, seller));
  }

  private ChatRoom findOrCreateChatRoom(Product product, User buyer, User seller) {
    return chatRoomRepository
        .findByProductAndBuyerAndSeller(product, buyer, seller)
        .orElseGet(() -> saveOrFindExisting(product, buyer, seller));
  }

  private ChatRoom saveOrFindExisting(Product product, User buyer, User seller) {
    try {
      return chatRoomRepository.saveAndFlush(ChatRoom.create(product, buyer, seller));
    } catch (DataIntegrityViolationException exception) {
      return chatRoomRepository
          .findByProductAndBuyerAndSeller(product, buyer, seller)
          .orElseThrow(() -> exception);
    }
  }

  private boolean isCreatableChatProduct(Product product) {
    return product.getDeletedAt() == null
        && product.getStatus() != ProductStatus.DELETED
        && !product.isHidden();
  }
}
