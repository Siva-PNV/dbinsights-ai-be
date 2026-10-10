package edu.dbinsight.api;

import edu.dbinsight.analysis.DatabaseHealthService;
import edu.dbinsight.analysis.QueryTelemetryCollector;
import edu.dbinsight.analysis.SchemaAnalysisService;
import edu.dbinsight.analysis.LiveRecommendationService;
import edu.dbinsight.api.ApiModels.AlertEventSummary;
import edu.dbinsight.api.ApiModels.AlertRuleSummary;
import edu.dbinsight.api.ApiModels.DatabaseSummary;
import edu.dbinsight.api.ApiModels.HealthScore;
import edu.dbinsight.api.ApiModels.IssueSummary;
import edu.dbinsight.api.ApiModels.LiveSchemaSummary;
import edu.dbinsight.api.ApiModels.QuerySummary;
import edu.dbinsight.api.ApiModels.QueryTelemetry;
import edu.dbinsight.api.ApiModels.RecommendationSummary;
import edu.dbinsight.api.ApiModels.LiveRecommendationResponse;
import edu.dbinsight.persistence.AlertingPersistenceService;
import edu.dbinsight.persistence.DatabaseRegistrationService;
import edu.dbinsight.persistence.MonitoringPersistenceService;
import edu.dbinsight.persistence.RecommendationPersistenceService;
import edu.dbinsight.persistence.TelemetryPersistenceService;
import java.util.List;
import edu.dbinsight.persistence.AdminRequestGuard;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "${app.frontend-origin:http://localhost:5173}")
public class AnalysisController {
    private final DatabaseHealthService databaseHealthService;
    private final QueryTelemetryCollector queryTelemetryCollector;
    private final SchemaAnalysisService schemaAnalysisService;
    private final LiveRecommendationService liveRecommendationService;
    private final MonitoringPersistenceService monitoringPersistenceService;
    private final DatabaseRegistrationService databaseRegistrationService;
    private final AlertingPersistenceService alertingPersistenceService;
    private final RecommendationPersistenceService recommendationPersistenceService;
    private final TelemetryPersistenceService telemetryPersistenceService;
    private final AdminRequestGuard adminRequestGuard;

    public AnalysisController(DatabaseHealthService databaseHealthService,
                              QueryTelemetryCollector queryTelemetryCollector,
                              SchemaAnalysisService schemaAnalysisService,
                              LiveRecommendationService liveRecommendationService,
                              MonitoringPersistenceService monitoringPersistenceService,
                              DatabaseRegistrationService databaseRegistrationService,
                              AlertingPersistenceService alertingPersistenceService,
                              RecommendationPersistenceService recommendationPersistenceService,
                              TelemetryPersistenceService telemetryPersistenceService,
                              AdminRequestGuard adminRequestGuard) {
        this.databaseHealthService = databaseHealthService;
        this.queryTelemetryCollector = queryTelemetryCollector;
        this.schemaAnalysisService = schemaAnalysisService;
        this.liveRecommendationService = liveRecommendationService;
        this.monitoringPersistenceService = monitoringPersistenceService;
        this.databaseRegistrationService = databaseRegistrationService;
        this.alertingPersistenceService = alertingPersistenceService;
        this.recommendationPersistenceService = recommendationPersistenceService;
        this.telemetryPersistenceService = telemetryPersistenceService;
        this.adminRequestGuard = adminRequestGuard;
    }

    @GetMapping("/databases")
    public List<DatabaseSummary> databases(Authentication authentication) {
        return monitoringPersistenceService.listDatabases(authentication.getName(), isAdmin(authentication));
    }

    @PostMapping("/databases")
    public DatabaseSummary createDatabase(@RequestHeader(value = "X-DBInsight-Token", required = false) String token,
                                        @RequestBody DatabaseRegistrationRequest request,
                                        Authentication authentication) {
        if ("dbinsight".equalsIgnoreCase(request.databaseName())) {
            adminRequestGuard.requireAdminRole(authentication);
        }
        adminRequestGuard.requireAdminToken(token);
        return databaseRegistrationService.register(
                authentication.getName(),
                request.databaseName(),
                request.dbType(),
                request.host(),
            request.port(),
            request.username(),
            request.password());
    }

    @DeleteMapping("/databases/{databaseId}")
    public void removeDatabase(Authentication authentication, @PathVariable Long databaseId) {
        String databaseName = monitoringPersistenceService
                .requireDatabaseAccess(databaseId, authentication.getName(), isAdmin(authentication))
                .getDatabaseName();
        if ("dbinsight".equals(databaseName)) {
            adminRequestGuard.requireAdminRole(authentication);
        }
        monitoringPersistenceService.removeDatabase(databaseId, authentication.getName(), isAdmin(authentication));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    @GetMapping("/metadata")
    public LiveSchemaSummary metadata(@RequestParam(required = false) Long databaseId, Authentication authentication) {
        requireTargetAccess(databaseId, authentication);
        return schemaAnalysisService.analyze(databaseId);
    }

    @GetMapping("/queries")
    public QueryTelemetry queries(@RequestParam(required = false) Long databaseId,
                                 @RequestParam(defaultValue = "false") boolean includeInternal,
                                 Authentication authentication) {
        requireTargetAccess(databaseId, authentication);
        return queryTelemetryCollector.snapshot(databaseId, includeInternal);
    }

    @GetMapping("/queries/slow")
    public QueryTelemetry slowQueries(@RequestParam(required = false) Long databaseId,
                                     @RequestParam(defaultValue = "false") boolean includeInternal,
                                     Authentication authentication) {
        requireTargetAccess(databaseId, authentication);
        QueryTelemetry snapshot = queryTelemetryCollector.snapshot(databaseId, includeInternal);
        return new QueryTelemetry(snapshot.queries().stream().filter(query -> "SLOW".equals(query.status())).toList(),
            snapshot.databaseName(), snapshot.available(), snapshot.message(), snapshot.collectedAt(),
            snapshot.slowQueryThresholdMs(), snapshot.internalQueryCount(), snapshot.internalQueriesIncluded(),
            snapshot.demoData());
    }

    @GetMapping("/queries/history")
    public List<QueryTelemetry> queryHistory(Authentication authentication) {
        return telemetryPersistenceService.recentSnapshots(authentication.getName(), isAdmin(authentication));
    }

    @GetMapping("/schema/issues")
    public List<IssueSummary> schemaIssues(@RequestParam(required = false) Long databaseId,
                                           Authentication authentication) {
        requireTargetAccess(databaseId, authentication);
        return schemaAnalysisService.analyze(databaseId).issues();
    }

    @GetMapping("/health")
    public HealthScore health(@RequestParam(required = false) Long databaseId, Authentication authentication) {
        requireTargetAccess(databaseId, authentication);
        return databaseHealthService.calculate(databaseId);
    }

    @GetMapping("/health/history")
    public List<HealthScore> healthHistory(@RequestParam(required = false) Long databaseId,
                                           Authentication authentication) {
        requireTargetAccess(databaseId, authentication);
        return monitoringPersistenceService.listHealthHistory(databaseId);
    }

    @GetMapping("/databases/{databaseId}/health")
    public HealthScore databaseHealth(@PathVariable Long databaseId, Authentication authentication) {
        requireTargetAccess(databaseId, authentication);
        return databaseHealthService.calculate(databaseId);
    }

    @GetMapping("/databases/{databaseId}/queries")
    public QueryTelemetry databaseQueries(@PathVariable Long databaseId,
                                          @RequestParam(defaultValue = "false") boolean includeInternal,
                                          Authentication authentication) {
        requireTargetAccess(databaseId, authentication);
        return queryTelemetryCollector.snapshot(databaseId, includeInternal);
    }

    @GetMapping("/alerts")
    public AlertSummary alerts() {
        return new AlertSummary(alertingPersistenceService.listRules(), alertingPersistenceService.listHistory());
    }

    @GetMapping("/alerts/rules")
    public List<AlertRuleSummary> alertRules() {
        return alertingPersistenceService.listRules();
    }

    @PostMapping("/alerts/rules")
    public AlertRuleSummary createAlertRule(@RequestHeader(value = "X-DBInsight-Token", required = false) String token,
                                          @RequestBody AlertRuleRequest request) {
        adminRequestGuard.requireAdminToken(token);
        return alertingPersistenceService.createRule(
                request.ruleName(),
                request.metric(),
                request.comparisonOperator(),
                request.thresholdValue(),
                request.severity(),
                request.enabled());
    }

    @GetMapping("/alerts/history")
    public List<AlertEventSummary> alertHistory() {
        return alertingPersistenceService.listHistory();
    }

    @GetMapping("/recommendations")
    public LiveRecommendationResponse recommendations(@RequestParam(required = false) Long databaseId,
                                                      Authentication authentication) {
        requireTargetAccess(databaseId, authentication);
        List<RecommendationSummary> persisted = recommendationPersistenceService.listRecommendations(databaseId);
        if (!liveRecommendationService.aiRecommendationsEnabled() && !persisted.isEmpty()) {
            return new LiveRecommendationResponse(
                    monitoringPersistenceService.resolveDatabase(databaseId).getDatabaseName(),
                    persisted,
                    java.time.Instant.now(),
                    false,
                    "PERSISTED",
                    "Recommendation history is being tracked in storage.");
        }
        return liveRecommendationService.recommendations(databaseId);
    }

    private void requireTargetAccess(Long databaseId, Authentication authentication) {
        String targetName = monitoringPersistenceService
                .requireDatabaseAccess(databaseId, authentication.getName(), isAdmin(authentication))
                .getDatabaseName();
        if ("dbinsight".equals(targetName) && !isAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "The internal dbinsight datasource is only available to ADMIN users.");
        }
    }

    @PostMapping("/recommendations")
    public RecommendationSummary createRecommendation(@RequestHeader(value = "X-DBInsight-Token", required = false) String token,
                                                   @RequestBody RecommendationRequest request) {
        adminRequestGuard.requireAdminToken(token);
        return recommendationPersistenceService.createRecommendation(
                request.databaseId(),
                request.issueId(),
                request.title(),
                request.reason(),
                request.severity(),
                request.status());
    }

    @PatchMapping("/recommendations/{recommendationId}/status")
    public RecommendationSummary updateRecommendationStatus(@RequestHeader(value = "X-DBInsight-Token", required = false) String token,
                                                          @PathVariable long recommendationId,
                                                          @RequestBody RecommendationStatusRequest request) {
        adminRequestGuard.requireAdminToken(token);
        RecommendationSummary recommendation = recommendationPersistenceService.listRecommendations(null).stream()
                .filter(item -> item.recommendationId() == recommendationId)
                .findFirst()
                .orElse(new RecommendationSummary(recommendationId, null, "Review recommendation",
                        "Status updated to a review state.", "MEDIUM", request.status()));
        return new RecommendationSummary(
                recommendation.recommendationId(),
                recommendation.issueId(),
                recommendation.title(),
                recommendation.reason(),
                recommendation.severity(),
                request.status());
    }

    public record DatabaseRegistrationRequest(String databaseName, String dbType, String host, Integer port,
                                              String username, String password) {}
    public record AlertRuleRequest(String ruleName, String metric, String comparisonOperator,
                                  Double thresholdValue, String severity, Boolean enabled) {}
    public record AlertSummary(List<AlertRuleSummary> rules, List<AlertEventSummary> history) {}
    public record RecommendationRequest(Long databaseId, Long issueId, String title, String reason,
                                        String severity, String status) {}
    public record RecommendationStatusRequest(String status) {}
}