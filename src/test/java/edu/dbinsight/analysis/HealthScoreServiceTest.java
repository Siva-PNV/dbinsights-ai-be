package edu.dbinsight.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.dbinsight.api.ApiModels.QuerySummary;
import edu.dbinsight.api.ApiModels.LiveIndex;
import java.util.List;
import org.junit.jupiter.api.Test;

class HealthScoreServiceTest {
    private final HealthScoreService service = new HealthScoreService();

    @Test
    void calculatesWeightedHealthScore() {
        assertEquals(86, service.calculate(91, 74, 83, 96));
    }

    @Test
    void rejectsScoresOutsideTheDocumentedRange() {
        assertThrows(IllegalArgumentException.class, () -> service.calculate(101, 74, 83, 96));
    }

    @Test
    void renormalizesWeightsWhenQueryTelemetryIsUnavailable() {
        assertEquals(79, service.calculateAvailable(null, 80, 60, 100));
        assertEquals(70, service.availableCoveragePercent(null, 80, 60, 100));
    }

    @Test
    void requiresAtLeastOneAvailableComponent() {
        assertThrows(IllegalArgumentException.class, () -> service.calculateAvailable(null, null, null, null));
    }

    @Test
    void scoresQueryLatencyAndPenalizesSlowDigests() {
        List<QuerySummary> queries = List.of(
                new QuerySummary(1, "digest-1", "SELECT 1", "dbinsight", "", 80, 100, 100, 100, "HEALTHY", false),
                new QuerySummary(2, "digest-2", "SELECT * FROM t", "dbinsight", "", 700, 10, 1000, 10, "SLOW", false));

        assertEquals(85, service.queryScore(queries, 500));
    }

    @Test
    void leavesQueryScoreUnavailableWhenNoDigestsExist() {
        assertEquals(null, service.queryScore(List.of(), 500));
    }

    @Test
    void recognizesForeignKeyColumnsOnlyWhenTheyLeadAnIndexInOrder() {
        assertEquals(true, SchemaAnalysisService.hasSupportingIndex(List.of("account_id", "region_id"),
                List.of(new LiveIndex("idx_account_region", false, List.of("account_id", "region_id", "created_at")))));
        assertEquals(false, SchemaAnalysisService.hasSupportingIndex(List.of("account_id", "region_id"),
                List.of(new LiveIndex("idx_region_account", false, List.of("region_id", "account_id")))));
    }
}