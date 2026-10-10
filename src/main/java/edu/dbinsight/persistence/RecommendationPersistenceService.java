package edu.dbinsight.persistence;

import edu.dbinsight.api.ApiModels.RecommendationSummary;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationPersistenceService {
    private final MonitoredDatabaseRepository monitoredDatabaseRepository;
    private final RecommendationRepository recommendationRepository;

    public RecommendationPersistenceService(MonitoredDatabaseRepository monitoredDatabaseRepository,
                                           RecommendationRepository recommendationRepository) {
        this.monitoredDatabaseRepository = monitoredDatabaseRepository;
        this.recommendationRepository = recommendationRepository;
    }

    @Transactional(readOnly = true)
    public List<RecommendationSummary> listRecommendations(Long databaseId) {
        Long targetDatabaseId = databaseId == null ? monitoredDatabaseRepository.findByDatabaseName("dbinsight")
                .map(MonitoredDatabaseEntity::getDatabaseId)
                .orElse(1L) : databaseId;
        return recommendationRepository.findByDatabaseDatabaseIdOrderByCreatedAtDesc(targetDatabaseId).stream()
                .map(entity -> new RecommendationSummary(
                        entity.getRecommendationId(),
                        entity.getIssueId(),
                        entity.getRecommendationText(),
                        entity.getReason(),
                        entity.getSeverity(),
                        entity.getStatus()))
                .toList();
    }

    @Transactional
    public RecommendationSummary createRecommendation(Long databaseId, Long issueId, String title,
                                                     String reason, String severity, String status) {
        MonitoredDatabaseEntity database = monitoredDatabaseRepository.findById(databaseId)
                .orElseGet(() -> monitoredDatabaseRepository.findByDatabaseName("dbinsight")
                        .orElseThrow(() -> new IllegalStateException("No monitored database available for recommendation.")));

        RecommendationEntity entity = recommendationRepository.save(new RecommendationEntity(
                database,
                issueId,
                title == null || title.isBlank() ? "Review recommendation" : title,
                reason == null || reason.isBlank() ? "Recommendation created from live findings." : reason,
                severity == null || severity.isBlank() ? "MEDIUM" : severity,
                status == null || status.isBlank() ? "PENDING_REVIEW" : status,
                Instant.now()));

        return new RecommendationSummary(
                entity.getRecommendationId(),
                entity.getIssueId(),
                entity.getRecommendationText(),
                entity.getReason(),
                entity.getSeverity(),
                entity.getStatus());
    }
}
