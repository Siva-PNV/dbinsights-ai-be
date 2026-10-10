package edu.dbinsight.persistence;

import edu.dbinsight.api.ApiModels.DatabaseSummary;
import edu.dbinsight.api.ApiModels.HealthScore;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MonitoringPersistenceService {
    private final MonitoredDatabaseRepository monitoredDatabaseRepository;
    private final MonitoredDatabaseCredentialRepository credentialRepository;
    private final HealthScoreRepository healthScoreRepository;

    public MonitoringPersistenceService(MonitoredDatabaseRepository monitoredDatabaseRepository,
                                       MonitoredDatabaseCredentialRepository credentialRepository,
                                       HealthScoreRepository healthScoreRepository) {
        this.monitoredDatabaseRepository = monitoredDatabaseRepository;
        this.credentialRepository = credentialRepository;
        this.healthScoreRepository = healthScoreRepository;
    }

    @Transactional
    public List<DatabaseSummary> listDatabases(String email, boolean isAdmin) {
        List<MonitoredDatabaseEntity> databases = isAdmin
                ? monitoredDatabaseRepository.findAllByOrderByDatabaseIdAsc()
                : monitoredDatabaseRepository.findByUserEmailOrderByDatabaseIdAsc(email);
        return databases.stream()
                .filter(database -> !database.isInternalDatabase())
                .map(database -> new DatabaseSummary(
                        database.getDatabaseId(),
                        database.getDatabaseName(),
                        database.getDbType(),
                        database.getStatus(),
                        database.getHost()))
                .toList();
    }

    @Transactional(readOnly = true)
    public MonitoredDatabaseEntity resolveDatabase(Long databaseId) {
        if (databaseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A databaseId is required.");
        }
        return monitoredDatabaseRepository.findById(databaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitored database was not found."));
    }

    @Transactional(readOnly = true)
    public MonitoredDatabaseEntity requireDatabaseAccess(Long databaseId, String email, boolean isAdmin) {
        if (databaseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A databaseId is required.");
        }
        MonitoredDatabaseEntity database = monitoredDatabaseRepository.findById(databaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitored database was not found."));
        if (!isAdmin && (database.getUser() == null || !email.equalsIgnoreCase(database.getUser().getEmail()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitored database was not found.");
        }
        return database;
    }

    @Transactional
    public void removeDatabase(Long databaseId) {
        MonitoredDatabaseEntity database = monitoredDatabaseRepository.findById(databaseId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitored database was not found."));
        credentialRepository.deleteById(databaseId);
        database.markRemoved();
        monitoredDatabaseRepository.save(database);
    }

    @Transactional
    public void removeDatabase(Long databaseId, String email, boolean isAdmin) {
        MonitoredDatabaseEntity database = requireDatabaseAccess(databaseId, email, isAdmin);
        credentialRepository.deleteById(databaseId);
        database.markRemoved();
        monitoredDatabaseRepository.save(database);
    }

    @Transactional(readOnly = true)
    public List<HealthScore> listHealthHistory(Long databaseId) {
        if (databaseId == null) {
            return List.of();
        }
        return healthScoreRepository.findByDatabaseDatabaseIdOrderByCalculatedAtDesc(databaseId).stream()
                .map(this::toHealthScore)
                .toList();
    }

    @Transactional
    public HealthScore saveHealthSnapshot(Long databaseId, HealthScore healthScore) {
        Objects.requireNonNull(healthScore, "healthScore is required");
        MonitoredDatabaseEntity database = monitoredDatabaseRepository.findById(databaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitored database was not found."));

        HealthScoreEntity entity = new HealthScoreEntity(
                database,
                toBigDecimal(healthScore.queryScore()),
                BigDecimal.valueOf(healthScore.indexScore()),
                BigDecimal.valueOf(healthScore.schemaScore()),
                BigDecimal.valueOf(healthScore.metadataScore()),
                BigDecimal.valueOf(healthScore.overallScore() == null ? 0 : healthScore.overallScore()),
                healthScore.calculatedAt()
        );

        HealthScoreEntity saved = healthScoreRepository.save(entity);
        return new HealthScore(
                healthScore.queryScore(),
                healthScore.indexScore(),
                healthScore.schemaScore(),
                healthScore.metadataScore(),
                healthScore.overallScore(),
                healthScore.status(),
                saved.getCalculatedAt(),
                healthScore.demoData(),
                healthScore.databaseName(),
                healthScore.tableCount(),
                healthScore.queryPerformanceAvailable(),
                healthScore.scoreCoveragePercent()
        );
    }

    private HealthScore toHealthScore(HealthScoreEntity entity) {
        return new HealthScore(
                entity.getQueryScore() == null ? null : entity.getQueryScore().intValue(),
                entity.getIndexScore().intValue(),
                entity.getSchemaScore().intValue(),
                entity.getMetadataScore().intValue(),
                entity.getOverallScore().intValue(),
                resolveStatus(entity),
                entity.getCalculatedAt(),
                false,
                entity.getDatabase().getDatabaseName(),
                0,
                entity.getQueryScore() != null,
                resolveCoverage(entity)
        );
    }

    private String resolveStatus(HealthScoreEntity entity) {
        if (entity.getOverallScore() == null) {
            return "INSUFFICIENT_DATA";
        }
        int value = entity.getOverallScore().intValue();
        if (value >= 80) {
            return "MEASURED";
        }
        if (value >= 50) {
            return "PARTIAL";
        }
        return "INSUFFICIENT_DATA";
    }

    private int resolveCoverage(HealthScoreEntity entity) {
        int coverage = 0;
        if (entity.getQueryScore() != null) {
            coverage += 30;
        }
        if (entity.getIndexScore() != null) {
            coverage += 25;
        }
        if (entity.getSchemaScore() != null) {
            coverage += 25;
        }
        if (entity.getMetadataScore() != null) {
            coverage += 20;
        }
        return coverage;
    }

    private BigDecimal toBigDecimal(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
