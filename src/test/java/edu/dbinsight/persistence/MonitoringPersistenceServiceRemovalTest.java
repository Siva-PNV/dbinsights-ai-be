package edu.dbinsight.persistence;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class MonitoringPersistenceServiceRemovalTest {
    @Test
    void removesCredentialsAndSoftRemovesTargetWithoutDeletingHistory() {
        MonitoredDatabaseRepository databaseRepository = mock(MonitoredDatabaseRepository.class);
        MonitoredDatabaseCredentialRepository credentialRepository = mock(MonitoredDatabaseCredentialRepository.class);
        HealthScoreRepository healthScoreRepository = mock(HealthScoreRepository.class);
        MonitoredDatabaseEntity target = mock(MonitoredDatabaseEntity.class);
        when(databaseRepository.findById(42L)).thenReturn(Optional.of(target));
        when(target.getDatabaseId()).thenReturn(42L);
        when(target.getDatabaseName()).thenReturn("student");

        MonitoringPersistenceService service = new MonitoringPersistenceService(
                databaseRepository, credentialRepository, healthScoreRepository);
        service.removeDatabase(42L);

        verify(credentialRepository).deleteById(42L);
        verify(target).markRemoved();
        verify(databaseRepository).save(target);
        verify(databaseRepository, never()).deleteById(42L);
        verifyNoInteractions(healthScoreRepository);
    }

    @Test
    void allowsSoftRemovalOfConfiguredDatabaseWithoutDeletingIt() {
        MonitoredDatabaseRepository databaseRepository = mock(MonitoredDatabaseRepository.class);
        MonitoredDatabaseCredentialRepository credentialRepository = mock(MonitoredDatabaseCredentialRepository.class);
        HealthScoreRepository healthScoreRepository = mock(HealthScoreRepository.class);
        MonitoredDatabaseEntity target = mock(MonitoredDatabaseEntity.class);
        when(databaseRepository.findById(1L)).thenReturn(Optional.of(target));
        when(target.getDatabaseName()).thenReturn("dbinsight");

        MonitoringPersistenceService service = new MonitoringPersistenceService(
                databaseRepository, credentialRepository, healthScoreRepository);

        service.removeDatabase(1L);

        verify(credentialRepository).deleteById(1L);
        verify(target).markRemoved();
        verify(databaseRepository).save(target);
        verify(databaseRepository, never()).deleteById(1L);
        verifyNoInteractions(healthScoreRepository);
    }
}