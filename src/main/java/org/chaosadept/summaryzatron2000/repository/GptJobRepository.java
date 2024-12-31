package org.chaosadept.summaryzatron2000.repository;

import org.chaosadept.summaryzatron2000.model.AsyncAnalyzeJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GptJobRepository extends JpaRepository<AsyncAnalyzeJob, UUID> {
    Optional<AsyncAnalyzeJob> findFirstByHasFinishedFalseOrderByStartDateAsc();
}
