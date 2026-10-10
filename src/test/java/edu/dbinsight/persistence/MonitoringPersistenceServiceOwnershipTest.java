package edu.dbinsight.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class MonitoringPersistenceServiceOwnershipTest {
    @Test
    void userCanOnlyListTheirOwnDatabases() {
        MonitoredDatabaseRepository databases = mock(MonitoredDatabaseRepository.class);
        MonitoredDatabaseEntity target = new MonitoredDatabaseEntity(
                        new AppUserEntity("User", "user@example.com", "hash", "USER", Instant.now()),
                        "personal_db", "MYSQL", "localhost", 3306, "ACTIVE", Instant.now());
        org.springframework.test.util.ReflectionTestUtils.setField(target, "databaseId", 12L);
        when(databases.findByUserEmailOrderByDatabaseIdAsc("user@example.com"))
                .thenReturn(List.of(target));
        MonitoringPersistenceService service = new MonitoringPersistenceService(
                databases, mock(MonitoredDatabaseCredentialRepository.class), mock(HealthScoreRepository.class));

        List<edu.dbinsight.api.ApiModels.DatabaseSummary> result =
                service.listDatabases("user@example.com", false);

        assertEquals(1, result.size());
        assertEquals("personal_db", result.get(0).databaseName());
    }

    @Test
    void nonOwnerCannotResolveAnotherUsersDatabase() {
        AppUserEntity owner = new AppUserEntity("Owner", "owner@example.com", "hash", "USER", Instant.now());
        MonitoredDatabaseEntity database = new MonitoredDatabaseEntity(
                owner, "private_db", "MYSQL", "localhost", 3306, "ACTIVE", Instant.now());
        MonitoredDatabaseRepository databases = mock(MonitoredDatabaseRepository.class);
        when(databases.findById(42L)).thenReturn(Optional.of(database));
        MonitoringPersistenceService service = new MonitoringPersistenceService(
                databases,
                mock(MonitoredDatabaseCredentialRepository.class), mock(HealthScoreRepository.class));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.requireDatabaseAccess(42L, "other@example.com", false));

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void ownerCanResolveTheirDatabase() {
        AppUserEntity owner = new AppUserEntity("Owner", "owner@example.com", "hash", "USER", Instant.now());
        MonitoredDatabaseEntity database = new MonitoredDatabaseEntity(
                owner, "private_db", "MYSQL", "localhost", 3306, "ACTIVE", Instant.now());
        MonitoredDatabaseRepository databases = mock(MonitoredDatabaseRepository.class);
        when(databases.findById(42L)).thenReturn(Optional.of(database));
        MonitoringPersistenceService service = new MonitoringPersistenceService(
                databases,
                mock(MonitoredDatabaseCredentialRepository.class), mock(HealthScoreRepository.class));

        assertEquals(database, service.requireDatabaseAccess(42L, "owner@example.com", false));
    }

    @Test
    void adminDatabaseListOmitsLegacyInternalApplicationStoreTarget() {
        AppUserEntity systemUser = new AppUserEntity(
                "System user", "system@dbinsight.local", "placeholder", "ADMIN", Instant.now());
        MonitoredDatabaseEntity internalTarget = new MonitoredDatabaseEntity(
                systemUser, "dbinsight", "MYSQL", "127.0.0.1", 3306, "ACTIVE", Instant.now());
        MonitoredDatabaseEntity externalTarget = new MonitoredDatabaseEntity(
                new AppUserEntity("Admin", "admin@example.com", "hash", "ADMIN", Instant.now()),
                "student_db", "MYSQL", "localhost", 3306, "ACTIVE", Instant.now());
        org.springframework.test.util.ReflectionTestUtils.setField(internalTarget, "databaseId", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(externalTarget, "databaseId", 2L);
        MonitoredDatabaseRepository databases = mock(MonitoredDatabaseRepository.class);
        when(databases.findAllByOrderByDatabaseIdAsc()).thenReturn(List.of(internalTarget, externalTarget));
        MonitoringPersistenceService service = new MonitoringPersistenceService(
                databases, mock(MonitoredDatabaseCredentialRepository.class), mock(HealthScoreRepository.class));

        List<edu.dbinsight.api.ApiModels.DatabaseSummary> result = service.listDatabases("admin@example.com", true);

        assertEquals(1, result.size());
        assertEquals("student_db", result.get(0).databaseName());
    }

    @Test
    void resolvingWithoutSelectedDatabaseRequiresDatabaseId() {
        MonitoringPersistenceService service = new MonitoringPersistenceService(
                mock(MonitoredDatabaseRepository.class),
                mock(MonitoredDatabaseCredentialRepository.class),
                mock(HealthScoreRepository.class));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.resolveDatabase(null));

        assertEquals(400, exception.getStatusCode().value());
    }
}
