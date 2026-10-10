package edu.dbinsight.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoredDatabaseCredentialRepository
        extends JpaRepository<MonitoredDatabaseCredentialEntity, Long> {
}