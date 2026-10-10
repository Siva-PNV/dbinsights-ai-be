package edu.dbinsight.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthScoreRepository extends JpaRepository<HealthScoreEntity, Long> {
    List<HealthScoreEntity> findByDatabaseDatabaseIdOrderByCalculatedAtDesc(Long databaseId);
}
