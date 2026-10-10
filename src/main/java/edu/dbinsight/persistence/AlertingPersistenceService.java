package edu.dbinsight.persistence;

import edu.dbinsight.api.ApiModels.AlertEventSummary;
import edu.dbinsight.api.ApiModels.AlertRuleSummary;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertingPersistenceService {
    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;

    public AlertingPersistenceService(AlertRuleRepository alertRuleRepository,
                                     AlertEventRepository alertEventRepository) {
        this.alertRuleRepository = alertRuleRepository;
        this.alertEventRepository = alertEventRepository;
    }

    @Transactional
    public List<AlertRuleSummary> listRules() {
        ensureDefaultRules();
        return alertRuleRepository.findAll(Sort.by(Sort.Direction.ASC, "ruleId")).stream()
                .map(rule -> new AlertRuleSummary(
                        rule.getRuleId(),
                        rule.getRuleName(),
                        rule.getMetric(),
                        rule.getComparisonOperator(),
                        rule.getThresholdValue(),
                        rule.getSeverity(),
                        rule.getEnabled(),
                        rule.getCreatedAt()))
                .toList();
    }

    @Transactional
    public AlertRuleSummary createRule(String ruleName, String metric, String comparisonOperator,
                                      Double thresholdValue, String severity, Boolean enabled) {
        AlertRuleEntity rule = alertRuleRepository.save(new AlertRuleEntity(
                ruleName == null || ruleName.isBlank() ? "Custom threshold" : ruleName.trim(),
                metric == null || metric.isBlank() ? "query_latency_ms" : metric.trim(),
                comparisonOperator == null || comparisonOperator.isBlank() ? ">" : comparisonOperator.trim(),
                thresholdValue == null ? 500.0 : thresholdValue,
                severity == null || severity.isBlank() ? "WARNING" : severity.trim().toUpperCase(),
                enabled == null || enabled,
                Instant.now()));

        return new AlertRuleSummary(
                rule.getRuleId(),
                rule.getRuleName(),
                rule.getMetric(),
                rule.getComparisonOperator(),
                rule.getThresholdValue(),
                rule.getSeverity(),
                rule.getEnabled(),
                rule.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<AlertEventSummary> listHistory() {
        Set<String> openAlerts = new HashSet<>();
        return alertEventRepository.findTop20ByOrderByOccurredAtDesc().stream()
                .filter(event -> !"OPEN".equals(event.getState())
                        || openAlerts.add(event.getRule().getRuleId() + "|" + event.getMessage()))
                .map(event -> new AlertEventSummary(
                        event.getEventId(),
                        event.getRule().getRuleName(),
                        event.getRule().getMetric(),
                        event.getSeverity(),
                        event.getState(),
                        event.getMessage(),
                        event.getMetricValue(),
                        event.getOccurredAt()))
                .toList();
    }

    @Transactional
    public AlertEventSummary recordEvent(String ruleName, String metric, double metricValue,
                                        String severity, String state, String message) {
        AlertRuleEntity rule = alertRuleRepository.findAll().stream()
                .filter(item -> item.getRuleName().equals(ruleName) || item.getMetric().equals(metric))
                .findFirst()
                .orElseGet(() -> alertRuleRepository.save(new AlertRuleEntity(
                        ruleName == null || ruleName.isBlank() ? "Auto event" : ruleName,
                        metric == null || metric.isBlank() ? "query_latency_ms" : metric,
                        ">",
                        500.0,
                        severity == null || severity.isBlank() ? "WARNING" : severity,
                        true,
                        Instant.now())));

        String eventSeverity = severity == null || severity.isBlank() ? rule.getSeverity() : severity;
        String eventState = state == null || state.isBlank() ? "OPEN" : state;
        String eventMessage = message == null || message.isBlank()
                ? "Alert triggered by threshold check."
                : message;
        if ("OPEN".equals(eventState)) {
            List<AlertEventEntity> existing = alertEventRepository
                    .findByRule_RuleIdAndStateAndMessageOrderByOccurredAtDesc(
                            rule.getRuleId(), eventState, eventMessage);
            if (!existing.isEmpty()) {
                return toSummary(existing.get(0));
            }
        }

        AlertEventEntity event = alertEventRepository.save(new AlertEventEntity(
                rule,
                metricValue,
                eventSeverity,
                eventState,
                eventMessage,
                Instant.now()));

        return toSummary(event);
    }

    @Transactional
    public void resolveOpenEvents(String ruleName, String metric, String message) {
        AlertRuleEntity rule = alertRuleRepository.findAll().stream()
                .filter(item -> item.getRuleName().equals(ruleName) || item.getMetric().equals(metric))
                .findFirst()
                .orElse(null);
        if (rule == null) {
            return;
        }
        alertEventRepository.findByRule_RuleIdAndStateAndMessageOrderByOccurredAtDesc(
                        rule.getRuleId(), "OPEN", message)
                .forEach(AlertEventEntity::resolve);
    }

    private AlertEventSummary toSummary(AlertEventEntity event) {
        return new AlertEventSummary(
                event.getEventId(),
                event.getRule().getRuleName(),
                event.getRule().getMetric(),
                event.getSeverity(),
                event.getState(),
                event.getMessage(),
                event.getMetricValue(),
                event.getOccurredAt());
    }

    private void ensureDefaultRules() {
        if (alertRuleRepository.count() == 0) {
            alertRuleRepository.saveAll(List.of(
                    new AlertRuleEntity("Slow query threshold", "query_latency_ms", ">", 500.0, "WARNING", true, Instant.now()),
                    new AlertRuleEntity("Schema issue threshold", "schema_findings", ">", 0.0, "MEDIUM", true, Instant.now())
            ));
        }
    }
}
