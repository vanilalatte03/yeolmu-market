package com.guingujig.yeolmumarket.domain.chat.repository;

import com.guingujig.yeolmumarket.domain.chat.entity.ChatRoom;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

  @EntityGraph(attributePaths = {"product", "buyer", "seller"})
  Optional<ChatRoom> findByProductAndBuyerAndSeller(Product product, User buyer, User seller);

  @EntityGraph(attributePaths = {"product", "buyer", "seller"})
  @Query(
      """
      select chatRoom
      from ChatRoom chatRoom
      where chatRoom.buyer.id = :userId or chatRoom.seller.id = :userId
      order by coalesce(chatRoom.lastMessageAt, chatRoom.createdAt) desc
      """)
  Page<ChatRoom> findParticipatingChatRooms(@Param("userId") Long userId, Pageable pageable);
}
