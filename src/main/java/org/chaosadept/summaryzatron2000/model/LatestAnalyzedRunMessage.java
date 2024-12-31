package org.chaosadept.summaryzatron2000.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "td_chat_latest_analyzed_message")
public class LatestAnalyzedRunMessage {
    @Id
    private Long chatId;
    private Long messageId;
    private Integer analyzeDateTime;
}
