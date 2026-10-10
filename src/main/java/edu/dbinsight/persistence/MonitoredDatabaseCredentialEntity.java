package edu.dbinsight.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "monitored_database_credential")
public class MonitoredDatabaseCredentialEntity {
    @Id
    @Column(name = "database_id")
    private Long databaseId;

    @Column(name = "encrypted_credentials", nullable = false, columnDefinition = "TEXT")
    private String encryptedCredentials;

    protected MonitoredDatabaseCredentialEntity() {
    }

    public MonitoredDatabaseCredentialEntity(Long databaseId, String encryptedCredentials) {
        this.databaseId = databaseId;
        this.encryptedCredentials = encryptedCredentials;
    }

    public Long getDatabaseId() {
        return databaseId;
    }

    public String getEncryptedCredentials() {
        return encryptedCredentials;
    }
}