package edu.dbinsight.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AiRecommendationExceptionHandlerTest {
    @Test
    void returnsGeminiConfigurationFailureAsApiDetail() {
        AiRecommendationExceptionHandler handler = new AiRecommendationExceptionHandler();
        AiRecommendationException exception = new AiRecommendationException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Gemini recommendations are enabled but GEMINI_API_KEY is not configured.");

        var response = handler.handleAiRecommendationException(exception);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(Map.of("detail", exception.getMessage()), response.getBody());
    }
}
