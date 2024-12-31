package org.chaosadept.summaryzatron2000.repository;

import org.chaosadept.summaryzatron2000.model.ChatLatestMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatLatestMessageRepository extends JpaRepository<ChatLatestMessage, Long> {
    Optional<ChatLatestMessage> findByChatId(Long chatId);
}
