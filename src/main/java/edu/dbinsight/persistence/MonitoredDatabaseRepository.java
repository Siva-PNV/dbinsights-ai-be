package edu.dbinsight.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoredDatabaseRepository extends JpaRepository<MonitoredDatabaseEntity, Long> {
    Optional<MonitoredDatabaseEntity> findByDatabaseName(String databaseName);
    Optional<MonitoredDatabaseEntity> findByDatabaseNameAndUserEmail(String databaseName, String email);
    Optional<MonitoredDatabaseEntity> findByDatabaseIdAndUserEmail(Long databaseId, String email);
    List<MonitoredDatabaseEntity> findByUserEmailOrderByDatabaseIdAsc(String email);
    List<MonitoredDatabaseEntity> findAllByOrderByDatabaseIdAsc();
    List<MonitoredDatabaseEntity> findByStatusOrderByDatabaseIdAsc(String status);
}
