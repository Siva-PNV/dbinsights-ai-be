package edu.dbinsight.analysis;

import edu.dbinsight.api.ApiModels.HealthScore;
import edu.dbinsight.api.ApiModels.QueryTelemetry;
import edu.dbinsight.persistence.AlertingPersistenceService;
import edu.dbinsight.persistence.DatabaseConnectionService;
import edu.dbinsight.persistence.MonitoredDatabaseEntity;
import edu.dbinsight.persistence.MonitoringPersistenceService;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DatabaseHealthService {
    private final DatabaseConnectionService databaseConnectionService;
    private final HealthScoreService healthScoreService;
    private final QueryTelemetryCollector queryTelemetryCollector;
    private final MonitoringPersistenceService monitoringPersistenceService;
    private final AlertingPersistenceService alertingPersistenceService;
    private final long slowQueryThresholdMs;

    public DatabaseHealthService(DatabaseConnectionService databaseConnectionService, HealthScoreService healthScoreService,
                                 QueryTelemetryCollector queryTelemetryCollector,
                                 MonitoringPersistenceService monitoringPersistenceService,
                                 AlertingPersistenceService alertingPersistenceService,
                                 @Value("${app.telemetry.slow-query-threshold-ms:500}") long slowQueryThresholdMs) {
        this.databaseConnectionService = databaseConnectionService;
        this.healthScoreService = healthScoreService;
        this.queryTelemetryCollector = queryTelemetryCollector;
        this.monitoringPersistenceService = monitoringPersistenceService;
        this.alertingPersistenceService = alertingPersistenceService;
        this.slowQueryThresholdMs = slowQueryThresholdMs;
    }

    public HealthScore calculate() {
        return calculate(null);
    }

    public HealthScore calculate(Long databaseId) {
        MonitoredDatabaseEntity target = monitoringPersistenceService.resolveDatabase(databaseId);
        String resolvedDatabaseName = target.getDatabaseName();
        try (Connection connection = databaseConnectionService.openConnection(target)) {
            List<String> tables = getTables(connection, resolvedDatabaseName);
            int tablesWithPrimaryKeys = 0;
            int tablesWithIndexes = 0;

            for (String table : tables) {
                if (hasPrimaryKey(connection, resolvedDatabaseName, table)) {
                    tablesWithPrimaryKeys++;
                }
                if (hasIndex(connection, resolvedDatabaseName, table)) {
                    tablesWithIndexes++;
                }
            }

            int indexScore = percentage(tablesWithIndexes, tables.size());
            int schemaScore = percentage(tablesWithPrimaryKeys, tables.size());
            int metadataScore = tables.isEmpty() ? 0 : 100;
                QueryTelemetry telemetry = queryTelemetryCollector.snapshot(target.getDatabaseId());
                Integer queryScore = telemetry.available()
                        ? healthScoreService.queryScore(telemetry.queries(), slowQueryThresholdMs)
                    : null;
            int overallScore = healthScoreService.calculateAvailable(queryScore, indexScore, schemaScore, metadataScore);
            int coverage = healthScoreService.availableCoveragePercent(queryScore, indexScore, schemaScore, metadataScore);
                String status = tables.isEmpty() ? "INSUFFICIENT_DATA" : queryScore == null ? "PARTIAL" : "MEASURED";

            HealthScore score = new HealthScore(queryScore, indexScore, schemaScore, metadataScore, overallScore,
                    status, Instant.now(), false, resolvedDatabaseName, tables.size(), queryScore != null, coverage);
            monitoringPersistenceService.saveHealthSnapshot(target.getDatabaseId(), score);
            recordThresholdAlerts(score);
            return score;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to inspect database " + resolvedDatabaseName + ".", exception);
        }
    }

    private List<String> getTables(Connection connection, String databaseName) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (ResultSet result = connection.getMetaData().getTables(databaseName, null, "%", new String[]{"TABLE"})) {
            while (result.next()) {
                tables.add(result.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private boolean hasPrimaryKey(Connection connection, String databaseName, String table) throws SQLException {
        try (ResultSet keys = connection.getMetaData().getPrimaryKeys(databaseName, null, table)) {
            return keys.next();
        }
    }

    private boolean hasIndex(Connection connection, String databaseName, String table) throws SQLException {
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(databaseName, null, table, false, false)) {
            while (indexes.next()) {
                if (indexes.getString("INDEX_NAME") != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private void recordThresholdAlerts(HealthScore score) {
        String overallHealthMessage = "Overall health score dropped below 60 for " + score.databaseName() + ".";
        if (score.overallScore() != null && score.overallScore() < 60) {
            alertingPersistenceService.recordEvent(
                    "Overall health threshold",
                    "overall_score",
                    score.overallScore(),
                    "HIGH",
                    "OPEN",
                    overallHealthMessage
            );
        } else if (score.overallScore() != null) {
            alertingPersistenceService.resolveOpenEvents(
                    "Overall health threshold", "overall_score", overallHealthMessage);
        }
        String queryPerformanceMessage = "Query performance score fell below 60 for " + score.databaseName() + ".";
        if (score.queryScore() != null && score.queryScore() < 60) {
            alertingPersistenceService.recordEvent(
                    "Query performance threshold",
                    "query_score",
                    score.queryScore(),
                    "WARNING",
                    "OPEN",
                    queryPerformanceMessage
            );
        } else if (score.queryScore() != null) {
            alertingPersistenceService.resolveOpenEvents(
                    "Query performance threshold", "query_score", queryPerformanceMessage);
        }
    }

    private int percentage(int numerator, int denominator) {
        if (denominator == 0) {
            return 0;
        }
        return (int) Math.round((double) numerator / denominator * 100);
    }
}