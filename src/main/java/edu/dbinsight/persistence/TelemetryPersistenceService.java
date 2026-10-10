package edu.dbinsight.persistence;

import edu.dbinsight.api.ApiModels.QuerySummary;
import edu.dbinsight.api.ApiModels.QueryTelemetry;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelemetryPersistenceService {
    private final MonitoredDatabaseRepository monitoredDatabaseRepository;
    private final TelemetrySnapshotRepository telemetrySnapshotRepository;

    public TelemetryPersistenceService(MonitoredDatabaseRepository monitoredDatabaseRepository,
                                      TelemetrySnapshotRepository telemetrySnapshotRepository) {
        this.monitoredDatabaseRepository = monitoredDatabaseRepository;
        this.telemetrySnapshotRepository = telemetrySnapshotRepository;
    }

    @Transactional
    public void saveSnapshot(Long databaseId, QueryTelemetry telemetry) {
        if (telemetry == null) {
            return;
        }
        String databaseName = telemetry.databaseName() == null ? "dbinsight" : telemetry.databaseName();
        MonitoredDatabaseEntity database = monitoredDatabaseRepository.findById(databaseId).orElse(null);
        String digestSummary = telemetry.queries().stream()
                .limit(10)
                .map(query -> query.queryText() + "::" + query.avgExecutionMs() + "ms")
                .reduce("", (left, right) -> left.isEmpty() ? right : left + " | " + right);

        telemetrySnapshotRepository.save(new TelemetrySnapshotEntity(
                database,
                databaseName,
                telemetry.available(),
                telemetry.message(),
                telemetry.collectedAt(),
                telemetry.slowQueryThresholdMs(),
                telemetry.internalQueryCount(),
                telemetry.internalQueriesIncluded(),
                telemetry.queries().size(),
                telemetry.demoData(),
                digestSummary
        ));
    }

    @Transactional(readOnly = true)
    public List<QueryTelemetry> recentSnapshots() {
        return telemetrySnapshotRepository.findTop20ByOrderByCollectedAtDesc().stream()
                .map(this::toQueryTelemetry)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QueryTelemetry> recentSnapshots(String email, boolean isAdmin) {
        List<TelemetrySnapshotEntity> snapshots = isAdmin
                ? telemetrySnapshotRepository.findTop20ByOrderByCollectedAtDesc()
                : telemetrySnapshotRepository.findTop20ByDatabase_User_EmailOrderByCollectedAtDesc(email);
        return snapshots.stream().map(this::toQueryTelemetry).toList();
    }

    private QueryTelemetry toQueryTelemetry(TelemetrySnapshotEntity snapshot) {
        return new QueryTelemetry(
                        List.of(),
                        snapshot.getDatabaseName(),
                        snapshot.isAvailable(),
                        snapshot.getMessage(),
                        snapshot.getCollectedAt(),
                        snapshot.getSlowQueryThresholdMs(),
                        snapshot.getInternalQueryCount(),
                        snapshot.isInternalQueriesIncluded(),
                        snapshot.isDemoData());
    }
}
