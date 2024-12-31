package org.chaosadept.summaryzatron2000.repository;

import org.chaosadept.summaryzatron2000.model.LatestAnalyzedRunMessage;
import org.chaosadept.summaryzatron2000.model.ChatLatestMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatLatestAnalyzedMessageRepository extends JpaRepository<LatestAnalyzedRunMessage, Long> {
    Optional<LatestAnalyzedRunMessage> findByChatId(Long chatId);
}
