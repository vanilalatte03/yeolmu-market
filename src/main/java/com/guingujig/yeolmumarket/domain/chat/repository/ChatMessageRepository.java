package com.guingujig.yeolmumarket.domain.chat.repository;

import com.guingujig.yeolmumarket.domain.chat.entity.ChatMessage;
import com.guingujig.yeolmumarket.domain.chat.entity.ChatRoom;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

  @EntityGraph(attributePaths = {"chatRoom", "sender"})
  @Query(
      """
      select message
      from ChatMessage message
      where message.chatRoom = :chatRoom
      and (
        :beforeMessageId is null
        or exists (
          select cursorMessage.id
          from ChatMessage cursorMessage
          where cursorMessage.chatRoom = :chatRoom
            and cursorMessage.id = :beforeMessageId
            and (
              message.createdAt < cursorMessage.createdAt
              or (message.createdAt = cursorMessage.createdAt and message.id < cursorMessage.id)
            )
        )
      )
      order by message.createdAt desc, message.id desc
      """)
  List<ChatMessage> findPreviousMessages(
      @Param("chatRoom") ChatRoom chatRoom,
      @Param("beforeMessageId") Long beforeMessageId,
      Pageable pageable);
}
