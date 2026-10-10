package edu.dbinsight.persistence;

import edu.dbinsight.api.ApiModels.DatabaseSummary;
import java.sql.SQLException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DatabaseRegistrationService {
    private final AppUserRepository appUserRepository;
    private final MonitoredDatabaseRepository monitoredDatabaseRepository;
    private final MonitoredDatabaseCredentialRepository credentialRepository;
    private final DatabaseCredentialEncryptionService encryptionService;
    private final DatabaseConnectionService connectionService;

    public DatabaseRegistrationService(AppUserRepository appUserRepository,
                                       MonitoredDatabaseRepository monitoredDatabaseRepository,
                                       MonitoredDatabaseCredentialRepository credentialRepository,
                                       DatabaseCredentialEncryptionService encryptionService,
                                       DatabaseConnectionService connectionService) {
        this.appUserRepository = appUserRepository;
        this.monitoredDatabaseRepository = monitoredDatabaseRepository;
        this.credentialRepository = credentialRepository;
        this.encryptionService = encryptionService;
        this.connectionService = connectionService;
    }

    @Transactional
    public DatabaseSummary register(String ownerEmail, String databaseName, String dbType, String host, Integer port,
                                   String username, String password) {
        DatabaseRegistrationPolicy.Registration registration;
        try {
            registration = DatabaseRegistrationPolicy.validate(databaseName, dbType, host, port);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        validateCredentials(username, password);
        MonitoredDatabaseEntity existing = monitoredDatabaseRepository
            .findByDatabaseNameAndUserEmail(registration.databaseName(), ownerEmail).orElse(null);
        if (existing != null && (!("PENDING".equals(existing.getStatus()) || "REMOVED".equals(existing.getStatus()))
            || credentialRepository.existsById(existing.getDatabaseId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A database with this name is already registered.");
        }

        String encryptedCredentials;
        try {
            encryptedCredentials = encryptionService.encrypt(username.trim(), password);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Credential encryption is not configured. Set DBINSIGHT_DATABASE_CREDENTIALS_KEY to a base64-encoded 32-byte key.");
        }

        try {
            connectionService.testConnection(registration, username.trim(), password);
        } catch (SQLException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Connection failed. Check the MySQL host, port, database name, credentials, TLS support, and account permissions.");
        }

        MonitoredDatabaseEntity database;
        if (existing != null) {
            existing.activate(registration.dbType(), registration.host(), registration.port());
            database = monitoredDatabaseRepository.save(existing);
        } else {
            AppUserEntity user = appUserRepository.findByEmail(ownerEmail)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to register a database."));
            database = monitoredDatabaseRepository.save(new MonitoredDatabaseEntity(
                user,
                registration.databaseName(),
                registration.dbType(),
                registration.host(),
                registration.port(),
                "ACTIVE",
                Instant.now()));
        }
        credentialRepository.save(new MonitoredDatabaseCredentialEntity(database.getDatabaseId(),
            encryptedCredentials));

        return new DatabaseSummary(database.getDatabaseId(), database.getDatabaseName(), database.getDbType(),
                database.getStatus(), database.getHost());
    }

    private void validateCredentials(String username, String password) {
        if (username == null || username.isBlank() || username.length() > 190) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Database username is required and must not exceed 190 characters.");
        }
        if (password == null || password.isEmpty() || password.length() > 4096) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Database password is required and must not exceed 4096 characters.");
        }
    }
}