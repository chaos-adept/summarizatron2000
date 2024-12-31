package org.chaosadept.summaryzatron2000.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Entity
@Table(name = "td_gpt_jobs")
public class AsyncAnalyzeJob {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private JobStage stage;

    @OneToMany(mappedBy = "jobId", fetch = FetchType.EAGER)
    private List<GptOperation> parts;
    private String workspaceDir;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "combinedSummaryOpId")
    private GptOperation combinedSummaryOperation;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "imagePromtOpId")
    private GptOperation imageSummaryPromtOperation;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "imageSummaryContentOpId")
    private GptOperation imageSummaryContentOperation;

    private Integer messageCount;
    private Long startDate;
    private Long finishDate;
    private Boolean hasFinished;
    private Boolean hasError;
}
