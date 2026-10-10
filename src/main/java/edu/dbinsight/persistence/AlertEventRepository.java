package edu.dbinsight.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertEventRepository extends JpaRepository<AlertEventEntity, Long> {
    List<AlertEventEntity> findTop20ByOrderByOccurredAtDesc();
    List<AlertEventEntity> findByRule_RuleIdAndStateAndMessageOrderByOccurredAtDesc(
            Long ruleId, String state, String message);
}
