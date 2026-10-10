package edu.dbinsight.analysis;

import edu.dbinsight.api.ApiModels.LiveRecommendationResponse;
import edu.dbinsight.api.ApiModels.LiveSchemaSummary;
import edu.dbinsight.api.ApiModels.QueryTelemetry;

public interface AiRecommendationGenerator {
    boolean enabled();

    LiveRecommendationResponse generate(
            LiveSchemaSummary schema, QueryTelemetry telemetry, long slowQueryThresholdMs);
}
