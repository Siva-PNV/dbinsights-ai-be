package edu.dbinsight.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DatabaseRegistrationServiceOwnershipTest {
    @Test
    void newlyRegisteredDatabaseIsOwnedByTheRequestingUser() throws Exception {
        AppUserEntity user = new AppUserEntity(
                "Account Owner", "owner@example.com", "hash", "USER", Instant.now());
        AppUserRepository users = mock(AppUserRepository.class);
        MonitoredDatabaseRepository databases = mock(MonitoredDatabaseRepository.class);
        MonitoredDatabaseCredentialRepository credentials = mock(MonitoredDatabaseCredentialRepository.class);
        DatabaseCredentialEncryptionService encryption = mock(DatabaseCredentialEncryptionService.class);
        DatabaseConnectionService connections = mock(DatabaseConnectionService.class);
        when(databases.findByDatabaseNameAndUserEmail("reports", "owner@example.com")).thenReturn(Optional.empty());
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        when(encryption.encrypt("monitor", "secret")).thenReturn("encrypted");
        when(databases.save(any(MonitoredDatabaseEntity.class)))
                .thenAnswer(invocation -> {
                    MonitoredDatabaseEntity database = invocation.getArgument(0);
                    org.springframework.test.util.ReflectionTestUtils.setField(database, "databaseId", 99L);
                    return database;
                });

        DatabaseRegistrationService service = new DatabaseRegistrationService(
                users, databases, credentials, encryption, connections);
        service.register("owner@example.com", "reports", "MYSQL", "localhost", 3306, "monitor", "secret");

        org.mockito.ArgumentCaptor<MonitoredDatabaseEntity> databaseCaptor =
                org.mockito.ArgumentCaptor.forClass(MonitoredDatabaseEntity.class);
        verify(databases).save(databaseCaptor.capture());
        verify(connections).testConnection(any(), org.mockito.ArgumentMatchers.eq("monitor"),
                org.mockito.ArgumentMatchers.eq("secret"));
        assertEquals("owner@example.com", databaseCaptor.getValue().getUser().getEmail());
    }
}
