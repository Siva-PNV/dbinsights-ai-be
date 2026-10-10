package edu.dbinsight.analysis;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.dbinsight.persistence.AppUserEntity;
import edu.dbinsight.persistence.DatabaseConnectionService;
import edu.dbinsight.persistence.MonitoredDatabaseEntity;
import edu.dbinsight.persistence.MonitoredDatabaseRepository;
import edu.dbinsight.persistence.MonitoringPersistenceService;
import edu.dbinsight.persistence.TelemetryPersistenceService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryTelemetryCollectorTest {
    @Test
    void startupCollectionSkipsApplicationStoreAndNonMysqlTargets() {
        DatabaseConnectionService connectionService = mock(DatabaseConnectionService.class);
        TelemetryPersistenceService telemetryPersistenceService = mock(TelemetryPersistenceService.class);
        MonitoringPersistenceService monitoringPersistenceService = mock(MonitoringPersistenceService.class);
        MonitoredDatabaseRepository databaseRepository = mock(MonitoredDatabaseRepository.class);
        MonitoredDatabaseEntity internalTarget = new MonitoredDatabaseEntity(
                new AppUserEntity("System user", "system@dbinsight.local", "placeholder", "ADMIN", Instant.now()),
                "dbinsight", "MYSQL", "127.0.0.1", 3306, "ACTIVE", Instant.now());
        MonitoredDatabaseEntity postgresTarget = new MonitoredDatabaseEntity(
                new AppUserEntity("User", "user@example.com", "hash", "USER", Instant.now()),
                "app_store", "POSTGRESQL", "localhost", 5432, "ACTIVE", Instant.now());
        when(databaseRepository.findByStatusOrderByDatabaseIdAsc("ACTIVE"))
                .thenReturn(List.of(internalTarget, postgresTarget));
        QueryTelemetryCollector collector = new QueryTelemetryCollector(
                connectionService,
                telemetryPersistenceService,
                monitoringPersistenceService,
                databaseRepository,
                500);

        collector.collectOnStartup();

        verify(databaseRepository).findByStatusOrderByDatabaseIdAsc("ACTIVE");
        verifyNoInteractions(connectionService, telemetryPersistenceService, monitoringPersistenceService);
    }
}
