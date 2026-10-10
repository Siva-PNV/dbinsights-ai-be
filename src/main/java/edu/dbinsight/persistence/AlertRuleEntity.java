package edu.dbinsight.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "alert_rule")
public class AlertRuleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(name = "metric", nullable = false)
    private String metric;

    @Column(name = "comparison_operator", nullable = false)
    private String comparisonOperator;

    @Column(name = "threshold_value", nullable = false)
    private Double thresholdValue;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AlertRuleEntity() {
    }

    public AlertRuleEntity(String ruleName, String metric, String comparisonOperator,
                          Double thresholdValue, String severity, Boolean enabled, Instant createdAt) {
        this.ruleName = ruleName;
        this.metric = metric;
        this.comparisonOperator = comparisonOperator;
        this.thresholdValue = thresholdValue;
        this.severity = severity;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getMetric() {
        return metric;
    }

    public String getComparisonOperator() {
        return comparisonOperator;
    }

    public Double getThresholdValue() {
        return thresholdValue;
    }

    public String getSeverity() {
        return severity;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
