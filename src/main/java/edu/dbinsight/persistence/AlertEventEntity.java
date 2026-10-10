package edu.dbinsight.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "alert_event")
public class AlertEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private AlertRuleEntity rule;

    @Column(name = "metric_value", nullable = false)
    private Double metricValue;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    protected AlertEventEntity() {
    }

    public AlertEventEntity(AlertRuleEntity rule, Double metricValue, String severity, String state,
                           String message, Instant occurredAt) {
        this.rule = rule;
        this.metricValue = metricValue;
        this.severity = severity;
        this.state = state;
        this.message = message;
        this.occurredAt = occurredAt;
    }

    public Long getEventId() {
        return eventId;
    }

    public AlertRuleEntity getRule() {
        return rule;
    }

    public Double getMetricValue() {
        return metricValue;
    }

    public String getSeverity() {
        return severity;
    }

    public String getState() {
        return state;
    }

    public void resolve() {
        this.state = "RESOLVED";
    }

    public String getMessage() {
        return message;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
