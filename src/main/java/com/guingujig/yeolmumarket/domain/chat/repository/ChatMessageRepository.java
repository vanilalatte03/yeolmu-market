package com.guingujig.yeolmumarket.domain.chat.repository;

import com.guingujig.yeolmumarket.domain.chat.entity.ChatMessage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

  @Query(
      """
      select message
      from ChatMessage message
      where message.chatRoom.id in :chatRoomIds
      and not exists (
        select 1
        from ChatMessage newerMessage
        where newerMessage.chatRoom = message.chatRoom
        and (
          newerMessage.createdAt > message.createdAt
          or (newerMessage.createdAt = message.createdAt and newerMessage.id > message.id)
        )
      )
      """)
  List<ChatMessage> findLatestMessagesByChatRoomIds(
      @Param("chatRoomIds") Collection<Long> chatRoomIds);
}
