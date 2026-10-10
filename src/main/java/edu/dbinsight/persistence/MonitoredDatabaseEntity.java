package edu.dbinsight.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "monitored_database")
public class MonitoredDatabaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "database_id")
    private Long databaseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUserEntity user;

    @Column(name = "database_name", nullable = false)
    private String databaseName;

    @Column(name = "db_type", nullable = false)
    private String dbType;

    @Column(name = "host", nullable = false)
    private String host;

    @Column(name = "port", nullable = false)
    private Integer port;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected MonitoredDatabaseEntity() {
    }

    public MonitoredDatabaseEntity(AppUserEntity user, String databaseName, String dbType,
                                  String host, Integer port, String status, Instant createdAt) {
        this.user = user;
        this.databaseName = databaseName;
        this.dbType = dbType;
        this.host = host;
        this.port = port;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getDatabaseId() {
        return databaseId;
    }

    public AppUserEntity getUser() {
        return user;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public boolean isInternalDatabase() {
        return "dbinsight".equalsIgnoreCase(databaseName)
                && user != null
                && "system@dbinsight.local".equalsIgnoreCase(user.getEmail());
    }

    public String getDbType() {
        return dbType;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public String getStatus() {
        return status;
    }

    public void activate(String dbType, String host, Integer port) {
        this.dbType = dbType;
        this.host = host;
        this.port = port;
        this.status = "ACTIVE";
    }

    public void markRemoved() {
        this.status = "REMOVED";
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
