package com.guingujig.yeolmumarket.domain.chat.repository;

import com.guingujig.yeolmumarket.domain.chat.entity.ChatRoom;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

  @EntityGraph(attributePaths = {"product", "buyer", "seller"})
  Optional<ChatRoom> findByProductAndBuyerAndSeller(Product product, User buyer, User seller);
}
