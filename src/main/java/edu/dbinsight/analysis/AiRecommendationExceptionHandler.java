package edu.dbinsight.analysis;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AiRecommendationExceptionHandler {
    @ExceptionHandler(AiRecommendationException.class)
    public ResponseEntity<Map<String, String>> handleAiRecommendationException(AiRecommendationException exception) {
        return ResponseEntity.status(exception.status())
                .body(Map.of("detail", exception.getMessage()));
    }
}
