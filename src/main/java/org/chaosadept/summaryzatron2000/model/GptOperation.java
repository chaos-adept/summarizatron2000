package org.chaosadept.summaryzatron2000.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Entity
@Table(name = "td_gpt_operation")
public class GptOperation {

    @Id
    private String operationId;

    private UUID jobId;
    @Enumerated(EnumType.STRING)
    private OperationType type;
    private Long startDate;
    private Long lastCheckDate;
    private Long finishedDate;

    @Lob
    @Column(name = "result", columnDefinition="BLOB")
    private String result;
    private Integer totalTokens;
    private Boolean hasFinished;
    private Boolean hasError;
    private String errorMessage;

}
