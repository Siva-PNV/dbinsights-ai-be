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
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "health_score")
public class HealthScoreEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "health_id")
    private Long healthId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "database_id", nullable = false)
    private MonitoredDatabaseEntity database;

    @Column(name = "query_score", precision = 5, scale = 2, nullable = true)
    private BigDecimal queryScore;

    @Column(name = "index_score", precision = 5, scale = 2, nullable = false)
    private BigDecimal indexScore;

    @Column(name = "schema_score", precision = 5, scale = 2, nullable = false)
    private BigDecimal schemaScore;

    @Column(name = "metadata_score", precision = 5, scale = 2, nullable = false)
    private BigDecimal metadataScore;

    @Column(name = "overall_score", precision = 5, scale = 2, nullable = false)
    private BigDecimal overallScore;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt = Instant.now();

    protected HealthScoreEntity() {
    }

    public HealthScoreEntity(MonitoredDatabaseEntity database, BigDecimal queryScore, BigDecimal indexScore,
                            BigDecimal schemaScore, BigDecimal metadataScore, BigDecimal overallScore,
                            Instant calculatedAt) {
        this.database = database;
        this.queryScore = queryScore;
        this.indexScore = indexScore;
        this.schemaScore = schemaScore;
        this.metadataScore = metadataScore;
        this.overallScore = overallScore;
        this.calculatedAt = calculatedAt;
    }

    public Long getHealthId() {
        return healthId;
    }

    public MonitoredDatabaseEntity getDatabase() {
        return database;
    }

    public BigDecimal getQueryScore() {
        return queryScore;
    }

    public BigDecimal getIndexScore() {
        return indexScore;
    }

    public BigDecimal getSchemaScore() {
        return schemaScore;
    }

    public BigDecimal getMetadataScore() {
        return metadataScore;
    }

    public BigDecimal getOverallScore() {
        return overallScore;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }
}
