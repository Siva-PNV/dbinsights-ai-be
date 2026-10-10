package edu.dbinsight.analysis;

import edu.dbinsight.api.ApiModels.IssueSummary;
import edu.dbinsight.api.ApiModels.LiveRecommendationResponse;
import edu.dbinsight.api.ApiModels.LiveSchemaSummary;
import edu.dbinsight.api.ApiModels.QuerySummary;
import edu.dbinsight.api.ApiModels.QueryTelemetry;
import edu.dbinsight.api.ApiModels.RecommendationSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LiveRecommendationService {
    private final SchemaAnalysisService schemaAnalysisService;
    private final QueryTelemetryCollector queryTelemetryCollector;
    private final AiRecommendationGenerator aiRecommendationGenerator;
    private final long slowQueryThresholdMs;
    private final long aiCacheTtlNanos;
    private final ConcurrentMap<Long, CachedRecommendation> aiRecommendationCache = new ConcurrentHashMap<>();

    public LiveRecommendationService(SchemaAnalysisService schemaAnalysisService,
                                     QueryTelemetryCollector queryTelemetryCollector,
                                     AiRecommendationGenerator aiRecommendationGenerator,
                                     @Value("${app.telemetry.slow-query-threshold-ms:500}") long slowQueryThresholdMs,
                                     @Value("${app.ai.recommendations.cache-ttl-ms:300000}") long aiCacheTtlMs) {
        this.schemaAnalysisService = schemaAnalysisService;
        this.queryTelemetryCollector = queryTelemetryCollector;
        this.aiRecommendationGenerator = aiRecommendationGenerator;
        this.slowQueryThresholdMs = slowQueryThresholdMs;
        this.aiCacheTtlNanos = Math.max(0, aiCacheTtlMs) * 1_000_000L;
    }

    public LiveRecommendationResponse recommendations() {
        return recommendations(null);
    }

    public LiveRecommendationResponse recommendations(Long databaseId) {
        LiveSchemaSummary schema = schemaAnalysisService.analyze(databaseId);
        QueryTelemetry telemetry = queryTelemetryCollector.snapshot(databaseId);
        List<QuerySummary> queries = telemetry.available() ? telemetry.queries() : List.of();
        boolean hasActionableEvidence = !schema.issues().isEmpty()
                || queries.stream().anyMatch(query -> query.avgExecutionMs() >= slowQueryThresholdMs);

        if (aiRecommendationGenerator.enabled() && hasActionableEvidence) {
            if (databaseId != null && aiCacheTtlNanos > 0) {
                long now = System.nanoTime();
                CachedRecommendation cached = aiRecommendationCache.compute(databaseId, (key, existing) -> {
                    long generatedAtNanos = System.nanoTime();
                    if (existing != null
                            && generatedAtNanos - existing.createdAtNanos() < aiCacheTtlNanos) {
                        return existing;
                    }
                    return new CachedRecommendation(
                            generatedAtNanos,
                            aiRecommendationGenerator.generate(schema, telemetry, slowQueryThresholdMs));
                });
                aiRecommendationCache.entrySet().removeIf(
                        entry -> now - entry.getValue().createdAtNanos() >= aiCacheTtlNanos);
                return cached.response();
            }
            return aiRecommendationGenerator.generate(schema, telemetry, slowQueryThresholdMs);
        }

        List<RecommendationSummary> recommendations =
                fromEvidence(schema.issues(), queries, slowQueryThresholdMs);
        String message = recommendations.isEmpty()
                ? "No active schema or slow-query finding is available for recommendations."
                : "Rule-based suggestions use current JDBC metadata and Performance Schema summaries.";
        return new LiveRecommendationResponse(
                schema.databaseName(), recommendations, Instant.now(), false, "RULE_BASED", message);
    }

    public boolean aiRecommendationsEnabled() {
        return aiRecommendationGenerator.enabled();
    }

    private record CachedRecommendation(long createdAtNanos, LiveRecommendationResponse response) {}

    static List<RecommendationSummary> fromEvidence(List<IssueSummary> issues, List<QuerySummary> queries,
                                                     long slowQueryThresholdMs) {
        List<RecommendationSummary> recommendations = new ArrayList<>();

        for (IssueSummary issue : issues) {
            if ("MISSING_PRIMARY_KEY".equals(issue.issueType())) {
                recommendations.add(new RecommendationSummary(recommendations.size() + 1L, issue.issueId(),
                        "Review a primary key for " + issue.tableName(),
                        issue.description() + " Identify a stable unique key with the application owner before changing the schema.",
                        issue.severity(), "PENDING_REVIEW"));
            } else if ("FOREIGN_KEY_WITHOUT_INDEX".equals(issue.issueType())) {
                recommendations.add(new RecommendationSummary(recommendations.size() + 1L, issue.issueId(),
                        "Evaluate an index for " + issue.tableName() + " foreign key",
                        issue.description() + " Validate the workload and index column order before applying a change.",
                        issue.severity(), "PENDING_REVIEW"));
            }
        }

        for (QuerySummary query : queries) {
            if (query.avgExecutionMs() < slowQueryThresholdMs) {
                continue;
            }
            String digest = query.digest();
            String digestLabel = digest == null || digest.isBlank()
                    ? "unavailable"
                    : digest.substring(0, Math.min(8, digest.length()));
            String severity = query.avgExecutionMs() >= slowQueryThresholdMs * 2 ? "HIGH" : "MEDIUM";
            recommendations.add(new RecommendationSummary(recommendations.size() + 1L, null,
                    "Review slow query digest " + digestLabel,
                    String.format("Observed %.2f ms average over %,d executions, examining %,d rows. Review the normalized statement with EXPLAIN and representative parameters before considering changes.",
                            query.avgExecutionMs(), query.executionCount(), query.rowsExamined()),
                    severity, "PENDING_REVIEW"));
        }

        return List.copyOf(recommendations);
    }
}