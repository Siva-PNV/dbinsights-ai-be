package edu.dbinsight.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelemetrySnapshotRepository extends JpaRepository<TelemetrySnapshotEntity, Long> {
    List<TelemetrySnapshotEntity> findTop20ByOrderByCollectedAtDesc();
    List<TelemetrySnapshotEntity> findTop20ByDatabase_User_EmailOrderByCollectedAtDesc(String email);
}
