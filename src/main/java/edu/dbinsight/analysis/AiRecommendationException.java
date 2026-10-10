package edu.dbinsight.analysis;

import org.springframework.http.HttpStatus;

public class AiRecommendationException extends RuntimeException {
    private final HttpStatus status;

    public AiRecommendationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public AiRecommendationException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
