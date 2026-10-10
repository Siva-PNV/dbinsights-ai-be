package edu.dbinsight.auth;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ResendEmailSenderTest {
    @Test
    void sendsMessageThroughResendHttpsApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendEmailSender sender = new ResendEmailSender(builder, "test-api-key");
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "from": "DBInsight <auth@example.com>",
                          "to": ["user@example.com"],
                          "subject": "Verify your DBInsight email",
                          "text": "Verification link"
                        }
                        """))
                .andRespond(withSuccess());

        sender.send("DBInsight <auth@example.com>", "user@example.com",
                "Verify your DBInsight email", "Verification link");

        server.verify();
    }

    @Test
    void reportsMissingApiKeyWithoutSendingRequest() {
        ResendEmailSender sender = new ResendEmailSender(RestClient.builder(), "");

        EmailDeliveryException exception = assertThrows(EmailDeliveryException.class,
                () -> sender.send("sender@example.com", "user@example.com", "Subject", "Body"));

        assertTrue(exception.getMessage().contains("RESEND_API_KEY"));
    }
}
