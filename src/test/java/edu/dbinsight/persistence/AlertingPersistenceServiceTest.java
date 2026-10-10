package edu.dbinsight.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlertingPersistenceServiceTest {
    @Test
    void doesNotCreateAnotherEventWhenTheSameAlertIsAlreadyOpen() {
        AlertRuleRepository rules = mock(AlertRuleRepository.class);
        AlertEventRepository events = mock(AlertEventRepository.class);
        AlertRuleEntity rule = mock(AlertRuleEntity.class);
        AlertEventEntity existing = mock(AlertEventEntity.class);
        when(rule.getRuleId()).thenReturn(3L);
        when(rule.getRuleName()).thenReturn("Overall health threshold");
        when(rule.getMetric()).thenReturn("overall_score");
        when(rule.getSeverity()).thenReturn("HIGH");
        when(rules.findAll()).thenReturn(List.of(rule));
        when(events.findByRule_RuleIdAndStateAndMessageOrderByOccurredAtDesc(
                3L, "OPEN", "Overall health score dropped below 60 for student."))
                .thenReturn(List.of(existing));
        when(existing.getEventId()).thenReturn(12L);
        when(existing.getRule()).thenReturn(rule);
        when(existing.getState()).thenReturn("OPEN");
        when(existing.getSeverity()).thenReturn("HIGH");
        when(existing.getMetricValue()).thenReturn(42.0);
        when(existing.getMessage()).thenReturn("Overall health score dropped below 60 for student.");
        when(existing.getOccurredAt()).thenReturn(Instant.EPOCH);

        AlertingPersistenceService service = new AlertingPersistenceService(rules, events);

        var result = service.recordEvent(
                "Overall health threshold",
                "overall_score",
                42.0,
                "HIGH",
                "OPEN",
                "Overall health score dropped below 60 for student.");

        assertEquals(12L, result.eventId());
        verify(events, never()).save(org.mockito.ArgumentMatchers.any(AlertEventEntity.class));
    }

    @Test
    void resolvesOpenEventsWhenTheConditionClears() {
        AlertRuleRepository rules = mock(AlertRuleRepository.class);
        AlertEventRepository events = mock(AlertEventRepository.class);
        AlertRuleEntity rule = mock(AlertRuleEntity.class);
        AlertEventEntity existing = mock(AlertEventEntity.class);
        when(rule.getRuleId()).thenReturn(3L);
        when(rule.getRuleName()).thenReturn("Overall health threshold");
        when(rule.getMetric()).thenReturn("overall_score");
        when(rules.findAll()).thenReturn(List.of(rule));
        when(events.findByRule_RuleIdAndStateAndMessageOrderByOccurredAtDesc(
                3L, "OPEN", "Overall health score dropped below 60 for student."))
                .thenReturn(List.of(existing));

        AlertingPersistenceService service = new AlertingPersistenceService(rules, events);

        service.resolveOpenEvents(
                "Overall health threshold",
                "overall_score",
                "Overall health score dropped below 60 for student.");

        verify(existing).resolve();
    }

    @Test
    void listsOnlyTheNewestCopyOfEachOpenAlert() {
        AlertRuleRepository rules = mock(AlertRuleRepository.class);
        AlertEventRepository events = mock(AlertEventRepository.class);
        AlertRuleEntity healthRule = mock(AlertRuleEntity.class);
        AlertRuleEntity queryRule = mock(AlertRuleEntity.class);
        when(healthRule.getRuleId()).thenReturn(3L);
        when(healthRule.getRuleName()).thenReturn("Overall health threshold");
        when(healthRule.getMetric()).thenReturn("overall_score");
        when(queryRule.getRuleId()).thenReturn(4L);
        when(queryRule.getRuleName()).thenReturn("Query performance threshold");
        when(queryRule.getMetric()).thenReturn("query_score");

        AlertEventEntity newestHealthAlert = event(healthRule, 12L, "OPEN",
                "Overall health score dropped below 60 for student.", Instant.ofEpochSecond(3));
        AlertEventEntity duplicateHealthAlert = event(healthRule, 11L, "OPEN",
                "Overall health score dropped below 60 for student.", Instant.ofEpochSecond(2));
        AlertEventEntity queryAlert = event(queryRule, 10L, "OPEN",
                "Query performance score fell below 60 for student.", Instant.ofEpochSecond(1));
        when(events.findTop20ByOrderByOccurredAtDesc())
                .thenReturn(List.of(newestHealthAlert, duplicateHealthAlert, queryAlert));

        AlertingPersistenceService service = new AlertingPersistenceService(rules, events);

        var history = service.listHistory();

        assertEquals(2, history.size());
        assertEquals(12L, history.get(0).eventId());
        assertEquals(10L, history.get(1).eventId());
    }

    private AlertEventEntity event(AlertRuleEntity rule, long eventId, String state, String message,
                                   Instant occurredAt) {
        AlertEventEntity event = mock(AlertEventEntity.class);
        when(event.getEventId()).thenReturn(eventId);
        when(event.getRule()).thenReturn(rule);
        when(event.getMetricValue()).thenReturn(42.0);
        when(event.getSeverity()).thenReturn("HIGH");
        when(event.getState()).thenReturn(state);
        when(event.getMessage()).thenReturn(message);
        when(event.getOccurredAt()).thenReturn(occurredAt);
        return event;
    }
}
