package com.safaricom.hr.repository;

import com.safaricom.hr.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {
    List<MessageEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
