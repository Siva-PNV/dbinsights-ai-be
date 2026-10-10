package edu.dbinsight.analysis;

import edu.dbinsight.api.ApiModels.QuerySummary;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HealthScoreService {
    public int calculate(int queryScore, int indexScore, int schemaScore, int metadataScore) {
        return calculateAvailable(queryScore, indexScore, schemaScore, metadataScore);
    }

    public int calculateAvailable(Integer queryScore, Integer indexScore, Integer schemaScore, Integer metadataScore) {
        Integer[] scores = {queryScore, indexScore, schemaScore, metadataScore};
        double[] weights = {0.30, 0.25, 0.25, 0.20};
        double weightedScore = 0;
        double availableWeight = 0;

        for (int index = 0; index < scores.length; index++) {
            Integer score = scores[index];
            if (score != null) {
                validateScore(score);
                weightedScore += score * weights[index];
                availableWeight += weights[index];
            }
        }

        if (availableWeight == 0) {
            throw new IllegalArgumentException("At least one health component score is required.");
        }
        return (int) Math.round(weightedScore / availableWeight);
    }

    public int availableCoveragePercent(Integer queryScore, Integer indexScore, Integer schemaScore, Integer metadataScore) {
        Integer[] scores = {queryScore, indexScore, schemaScore, metadataScore};
        double[] weights = {0.30, 0.25, 0.25, 0.20};
        double availableWeight = 0;
        for (int index = 0; index < scores.length; index++) {
            if (scores[index] != null) {
                availableWeight += weights[index];
            }
        }
        return (int) Math.round(availableWeight * 100);
    }

    public Integer queryScore(List<QuerySummary> queries, long slowThresholdMs) {
        if (queries.isEmpty()) {
            return null;
        }

        double weightedLatency = 0;
        long totalExecutions = 0;
        int slowQueries = 0;
        for (QuerySummary query : queries) {
            long executions = Math.max(query.executionCount(), 1);
            weightedLatency += query.avgExecutionMs() * executions;
            totalExecutions += executions;
            if (query.avgExecutionMs() >= slowThresholdMs) {
                slowQueries++;
            }
        }

        double averageLatency = weightedLatency / totalExecutions;
        int latencyScore = averageLatency <= 100 ? 100
                : averageLatency <= 250 ? 90
                : averageLatency <= 500 ? 75
                : averageLatency <= 1_000 ? 60 : 40;
        return Math.max(0, latencyScore - Math.min(30, slowQueries * 5));
    }

    private void validateScore(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Health component scores must be between 0 and 100.");
        }
    }
}