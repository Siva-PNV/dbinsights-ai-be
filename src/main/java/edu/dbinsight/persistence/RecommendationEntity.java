package edu.dbinsight.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "recommendation")
public class RecommendationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_id")
    private Long recommendationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "database_id", nullable = false)
    private MonitoredDatabaseEntity database;

    @Column(name = "issue_id")
    private Long issueId;

    @Column(name = "recommendation_text", nullable = false)
    private String recommendationText;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "status", nullable = false)
    private String status = "PENDING_REVIEW";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected RecommendationEntity() {
    }

    public RecommendationEntity(MonitoredDatabaseEntity database, Long issueId, String recommendationText,
                               String reason, String severity, String status, Instant createdAt) {
        this.database = database;
        this.issueId = issueId;
        this.recommendationText = recommendationText;
        this.reason = reason;
        this.severity = severity;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getRecommendationId() {
        return recommendationId;
    }

    public MonitoredDatabaseEntity getDatabase() {
        return database;
    }

    public Long getIssueId() {
        return issueId;
    }

    public String getRecommendationText() {
        return recommendationText;
    }

    public String getReason() {
        return reason;
    }

    public String getSeverity() {
        return severity;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
