package edu.dbinsight.analysis;

import edu.dbinsight.api.ApiModels.QuerySummary;
import edu.dbinsight.api.ApiModels.QueryTelemetry;
import edu.dbinsight.persistence.DatabaseConnectionService;
import edu.dbinsight.persistence.MonitoredDatabaseEntity;
import edu.dbinsight.persistence.MonitoredDatabaseRepository;
import edu.dbinsight.persistence.MonitoringPersistenceService;
import edu.dbinsight.persistence.TelemetryPersistenceService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class QueryTelemetryCollector {
    private static final String DIGEST_QUERY = """
            SELECT DIGEST, DIGEST_TEXT, COUNT_STAR, AVG_TIMER_WAIT,
                   SUM_ROWS_EXAMINED, SUM_ROWS_SENT
            FROM performance_schema.events_statements_summary_by_digest
            WHERE SCHEMA_NAME = ? AND DIGEST_TEXT IS NOT NULL AND COUNT_STAR > 0
            ORDER BY SUM_TIMER_WAIT DESC
            LIMIT 100
            """;

    private final DatabaseConnectionService databaseConnectionService;
    private final TelemetryPersistenceService telemetryPersistenceService;
    private final MonitoringPersistenceService monitoringPersistenceService;
    private final MonitoredDatabaseRepository monitoredDatabaseRepository;
    private final long slowQueryThresholdMs;
    private final Map<Long, QueryTelemetry> latestByDatabaseId = new ConcurrentHashMap<>();

    public QueryTelemetryCollector(
            DatabaseConnectionService databaseConnectionService,
            TelemetryPersistenceService telemetryPersistenceService,
            MonitoringPersistenceService monitoringPersistenceService,
            MonitoredDatabaseRepository monitoredDatabaseRepository,
            @Value("${app.telemetry.slow-query-threshold-ms:500}") long slowQueryThresholdMs) {
        this.databaseConnectionService = databaseConnectionService;
        this.telemetryPersistenceService = telemetryPersistenceService;
        this.monitoringPersistenceService = monitoringPersistenceService;
        this.monitoredDatabaseRepository = monitoredDatabaseRepository;
        this.slowQueryThresholdMs = slowQueryThresholdMs;
    }

    @Scheduled(fixedDelayString = "${app.telemetry.collection-interval-ms:30000}")
    public void collectOnSchedule() {
        collectActiveTargets();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void collectOnStartup() {
        collectActiveTargets();
    }

    public QueryTelemetry snapshot() {
        return snapshot(null, false);
    }

    public QueryTelemetry snapshot(Long databaseId) {
        return snapshot(databaseId, false);
    }

    public QueryTelemetry snapshot(boolean includeInternal) {
        return snapshot(null, includeInternal);
    }

    public QueryTelemetry snapshot(Long databaseId, boolean includeInternal) {
        MonitoredDatabaseEntity target = monitoringPersistenceService.resolveDatabase(databaseId);
        QueryTelemetry collected;
        if (!"ACTIVE".equals(target.getStatus())) {
            collected = unavailable(target.getDatabaseName(), "This database target is not active.");
        } else {
            collected = latestByDatabaseId.get(target.getDatabaseId());
            if (collected == null) {
                collected = collectTarget(target);
            }
        }
        List<QuerySummary> internalQueries = collected.queries().stream().filter(QuerySummary::internal).toList();
        List<QuerySummary> visibleQueries = collected.queries().stream()
            .filter(query -> includeInternal || !query.internal())
            .toList();
        String message = visibleQueries.isEmpty() && !collected.queries().isEmpty() && !includeInternal
            ? "Only internal/system metadata statements were collected. Enable Include internal to inspect them."
            : collected.message();
        return new QueryTelemetry(visibleQueries, collected.databaseName(), !visibleQueries.isEmpty(), message,
            collected.collectedAt(), collected.slowQueryThresholdMs(), internalQueries.size(), includeInternal,
            collected.demoData());
    }

    public QueryTelemetry collect() {
        return collect(null);
    }

    public QueryTelemetry collect(Long databaseId) {
        return collectTarget(monitoringPersistenceService.resolveDatabase(databaseId));
    }

    private void collectActiveTargets() {
        monitoredDatabaseRepository.findByStatusOrderByDatabaseIdAsc("ACTIVE")
                .stream()
                .filter(target -> "MYSQL".equalsIgnoreCase(target.getDbType()))
                .filter(target -> !target.isInternalDatabase())
                .forEach(this::collectTarget);
    }

    private QueryTelemetry collectTarget(MonitoredDatabaseEntity target) {
        QueryTelemetry collected;
        String databaseName = target.getDatabaseName();
        try (Connection connection = databaseConnectionService.openConnection(target)) {
            if (!isPerformanceSchemaEnabled(connection)) {
                collected = unavailable(databaseName, "MySQL Performance Schema is disabled for this target.");
                latestByDatabaseId.put(target.getDatabaseId(), collected);
                telemetryPersistenceService.saveSnapshot(target.getDatabaseId(), collected);
                return collected;
            }

            List<QuerySummary> queries = readDigestSummaries(connection, databaseName);
            String message = queries.isEmpty()
                    ? "No statement digests are recorded for the configured schema yet."
                    : "MySQL Performance Schema statement digests collected.";
            collected = new QueryTelemetry(queries, databaseName, !queries.isEmpty(), message, Instant.now(),
                    slowQueryThresholdMs, (int) queries.stream().filter(QuerySummary::internal).count(), false, false);
        } catch (SQLException exception) {
            collected = unavailable(databaseName,
                    "Performance Schema is unavailable or the target account lacks SELECT permission.");
        }
        latestByDatabaseId.put(target.getDatabaseId(), collected);
        telemetryPersistenceService.saveSnapshot(target.getDatabaseId(), collected);
        return collected;
    }

    private boolean isPerformanceSchemaEnabled(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT @@performance_schema");
             ResultSet result = statement.executeQuery()) {
            return result.next() && result.getBoolean(1);
        }
    }

    private List<QuerySummary> readDigestSummaries(Connection connection, String databaseName) throws SQLException {
        List<QuerySummary> queries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(DIGEST_QUERY)) {
            statement.setString(1, databaseName);
            try (ResultSet result = statement.executeQuery()) {
                long id = 1;
                while (result.next()) {
                    String digest = result.getString("DIGEST");
                    String digestText = result.getString("DIGEST_TEXT");
                    long executionCount = result.getLong("COUNT_STAR");
                    double averageExecutionMs = result.getDouble("AVG_TIMER_WAIT") / 1_000_000_000.0;
                    long rowsExamined = result.getLong("SUM_ROWS_EXAMINED");
                    long rowsReturned = result.getLong("SUM_ROWS_SENT");
                    String status = averageExecutionMs >= slowQueryThresholdMs ? "SLOW" : "HEALTHY";

                        queries.add(new QuerySummary(id++, digest, digestText, databaseName, "",
                            averageExecutionMs,
                            executionCount, rowsExamined, rowsReturned, status, StatementClassifier.isInternal(digestText)));
                }
            }
        }
        return List.copyOf(queries);
    }

    private QueryTelemetry unavailable(String databaseName, String message) {
        return new QueryTelemetry(List.of(), databaseName, false, message, Instant.now(), slowQueryThresholdMs,
            0, false, false);
    }
}