package edu.dbinsight.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.dbinsight.api.ApiModels.IssueSummary;
import edu.dbinsight.api.ApiModels.LiveSchemaSummary;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiRecommendationSupportTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesGeminiJsonAndRejectsUnknownIssueIds() throws Exception {
        var response = objectMapper.readTree("""
                {
                  "steps": [{
                    "type": "model_output",
                    "content": [{
                      "type": "text",
                      "text": "{\\"recommendations\\":[{\\"title\\":\\"Review the orders key\\",\\"reason\\":\\"The orders table has no primary key.\\",\\"severity\\":\\"medium\\",\\"issueId\\":44}]}"
                    }]
                  }]
                }
                """);
        var schema = new LiveSchemaSummary(
                "shop",
                List.of(),
                List.of(new IssueSummary(7, "orders", "MISSING_PRIMARY_KEY",
                        "No primary key was reported.", "MEDIUM", "OPEN")),
                Instant.now(),
                false);

        var recommendations = AiRecommendationSupport.parseGeminiResponse(response, objectMapper, schema);

        assertEquals(1, recommendations.size());
        assertEquals("MEDIUM", recommendations.get(0).severity());
        assertNull(recommendations.get(0).issueId());
    }

    @Test
    void requestsJsonStructuredOutputFromInteractionsApi() {
        var responseFormat = AiRecommendationSupport.responseFormat();

        assertEquals("text", responseFormat.get("type"));
        assertEquals("application/json", responseFormat.get("mime_type"));
        assertEquals("object", ((java.util.Map<?, ?>) responseFormat.get("schema")).get("type"));
    }
}
