package edu.dbinsight.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRepository extends JpaRepository<RecommendationEntity, Long> {
    List<RecommendationEntity> findByDatabaseDatabaseIdOrderByCreatedAtDesc(Long databaseId);
}
