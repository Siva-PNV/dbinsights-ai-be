package edu.dbinsight.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    public OpenAPI dbInsightOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("DBInsight API")
                        .version("1.0.0")
                        .description("""
                                API for DBInsight accounts, MySQL monitoring targets, database health,
                                schema metadata, query telemetry, recommendations, and alerts.

                                DBInsight application data is stored in PostgreSQL. Registered monitoring
                                targets currently support MySQL only. Analysis endpoints require a registered
                                databaseId owned by the signed-in user or accessible to an administrator.
                                Recommendations are advisory and never execute SQL.
                                """))
                .components(new Components()
                        .addSecuritySchemes("sessionCookie", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("Session cookie issued by POST /api/auth/login."))
                        .addSecuritySchemes("adminToken", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-DBInsight-Token")
                                .description("Admin token required by protected write operations.")));
    }
}
