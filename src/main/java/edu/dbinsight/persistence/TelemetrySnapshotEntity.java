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
@Table(name = "telemetry_snapshot")
public class TelemetrySnapshotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long snapshotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "database_id")
    private MonitoredDatabaseEntity database;

    @Column(name = "database_name", nullable = false)
    private String databaseName;

    @Column(name = "available", nullable = false)
    private boolean available;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    @Column(name = "slow_query_threshold_ms", nullable = false)
    private long slowQueryThresholdMs;

    @Column(name = "internal_query_count", nullable = false)
    private int internalQueryCount;

    @Column(name = "internal_queries_included", nullable = false)
    private boolean internalQueriesIncluded;

    @Column(name = "query_count", nullable = false)
    private int queryCount;

    @Column(name = "demo_data", nullable = false)
    private boolean demoData;

    @Column(name = "digest_summary", nullable = false, columnDefinition = "TEXT")
    private String digestSummary;

    protected TelemetrySnapshotEntity() {
    }

    public TelemetrySnapshotEntity(MonitoredDatabaseEntity database, String databaseName, boolean available,
                                  String message, Instant collectedAt, long slowQueryThresholdMs,
                                  int internalQueryCount, boolean internalQueriesIncluded,
                                  int queryCount, boolean demoData, String digestSummary) {
        this.database = database;
        this.databaseName = databaseName;
        this.available = available;
        this.message = message;
        this.collectedAt = collectedAt;
        this.slowQueryThresholdMs = slowQueryThresholdMs;
        this.internalQueryCount = internalQueryCount;
        this.internalQueriesIncluded = internalQueriesIncluded;
        this.queryCount = queryCount;
        this.demoData = demoData;
        this.digestSummary = digestSummary;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public MonitoredDatabaseEntity getDatabase() {
        return database;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }

    public long getSlowQueryThresholdMs() {
        return slowQueryThresholdMs;
    }

    public int getInternalQueryCount() {
        return internalQueryCount;
    }

    public boolean isInternalQueriesIncluded() {
        return internalQueriesIncluded;
    }

    public int getQueryCount() {
        return queryCount;
    }

    public boolean isDemoData() {
        return demoData;
    }

    public String getDigestSummary() {
        return digestSummary;
    }
}
