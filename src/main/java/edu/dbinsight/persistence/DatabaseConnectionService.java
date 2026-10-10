package edu.dbinsight.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseConnectionService {
    private final MonitoredDatabaseCredentialRepository credentialRepository;
    private final DatabaseCredentialEncryptionService encryptionService;

    public DatabaseConnectionService(MonitoredDatabaseCredentialRepository credentialRepository,
                                     DatabaseCredentialEncryptionService encryptionService) {
        this.credentialRepository = credentialRepository;
        this.encryptionService = encryptionService;
    }

    public Connection openConnection(MonitoredDatabaseEntity target) throws SQLException {
        if (!"ACTIVE".equals(target.getStatus())) {
            throw new SQLException("The monitored database is not active.");
        }
        MonitoredDatabaseCredentialEntity secret = credentialRepository.findById(target.getDatabaseId()).orElse(null);
        if (secret == null) {
            throw new SQLException("Credentials are not configured for this database.");
        }

        DatabaseCredentialEncryptionService.Credentials credentials =
                encryptionService.decrypt(secret.getEncryptedCredentials());
        return DriverManager.getConnection(jdbcUrl(target.getHost(), target.getPort(), target.getDatabaseName()),
                credentials.username(), credentials.password());
    }

    public void testConnection(DatabaseRegistrationPolicy.Registration target, String username, String password)
            throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl(target.host(), target.port(), target.databaseName()), username, password)) {
            if (!connection.isValid(5)) {
                throw new SQLException("The database did not pass the JDBC connection check.");
            }
        }
    }

    private String jdbcUrl(String host, int port, String databaseName) {
        return "jdbc:mysql://" + host + ":" + port + "/" + databaseName
                + "?connectTimeout=5000&socketTimeout=10000&sslMode=REQUIRED";
    }
}