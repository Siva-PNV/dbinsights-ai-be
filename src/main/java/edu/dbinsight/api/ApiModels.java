package edu.dbinsight.api;

import java.time.Instant;
import java.util.List;

public final class ApiModels {
    private ApiModels() {}

    public record DatabaseSummary(long databaseId, String databaseName, String dbType, String status, String host) {}

    public record HealthScore(Integer queryScore, int indexScore, int schemaScore, int metadataScore,
                              Integer overallScore, String status, Instant calculatedAt, boolean demoData,
                              String databaseName, int tableCount, boolean queryPerformanceAvailable,
                              int scoreCoveragePercent) {}

    public record QuerySummary(long queryId, String digest, String queryText, String schemaName, String tableName,
                               double avgExecutionMs, long executionCount, long rowsExamined, long rowsReturned,
                                                             String status, boolean internal) {}

    public record QueryTelemetry(List<QuerySummary> queries, String databaseName, boolean available,
                                                                 String message, Instant collectedAt, long slowQueryThresholdMs,
                                                                 int internalQueryCount, boolean internalQueriesIncluded, boolean demoData) {}

    public record IssueSummary(long issueId, String tableName, String issueType, String description,
                               String severity, String status) {}

    public record RecommendationSummary(long recommendationId, Long issueId, String title, String reason,
                                       String severity, String status) {}

    public record AlertRuleSummary(long ruleId, String ruleName, String metric, String comparisonOperator,
                                  double thresholdValue, String severity, boolean enabled, Instant createdAt) {}

    public record AlertEventSummary(long eventId, String ruleName, String metric, String severity,
                                   String state, String message, double metricValue, Instant occurredAt) {}

    public record LiveRecommendationResponse(String databaseName, List<RecommendationSummary> recommendations,
                                             Instant generatedAt, boolean demoData, String source, String message) {}

    public record LiveSchemaSummary(String databaseName, List<LiveSchemaTable> tables, List<IssueSummary> issues,
                                    Instant collectedAt, boolean demoData) {}

    public record LiveSchemaTable(String tableName, List<LiveColumn> columns, List<String> primaryKeyColumns,
                                  List<LiveForeignKey> foreignKeys, List<LiveIndex> indexes) {}

    public record LiveColumn(String columnName, String dataType, boolean nullable, int ordinalPosition) {}

    public record LiveForeignKey(String constraintName, String columnName, String referencedTable,
                                 String referencedColumn, int sequence) {}

    public record LiveIndex(String indexName, boolean unique, List<String> columns) {}
}