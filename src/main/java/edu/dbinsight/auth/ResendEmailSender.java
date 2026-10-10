package edu.dbinsight.auth;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(prefix = "app.mail", name = "provider", havingValue = "resend")
public class ResendEmailSender implements EmailSender {
    private final RestClient restClient;
    private final String apiKey;

    public ResendEmailSender(RestClient.Builder restClientBuilder,
                             @Value("${app.mail.resend.api-key:}") String apiKey) {
        this.restClient = restClientBuilder.baseUrl("https://api.resend.com").build();
        this.apiKey = apiKey;
    }

    @Override
    public void send(String from, String to, String subject, String text) {
        if (apiKey.isBlank()) {
            throw new EmailDeliveryException("RESEND_API_KEY is not configured.");
        }

        try {
            restClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ResendEmailRequest(from, List.of(to), subject, text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new EmailDeliveryException("Resend email API request failed.", exception);
        }
    }

    private record ResendEmailRequest(String from, List<String> to, String subject, String text) {}
}
