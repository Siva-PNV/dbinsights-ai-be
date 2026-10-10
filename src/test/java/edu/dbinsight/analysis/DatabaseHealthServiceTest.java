package edu.dbinsight.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.dbinsight.api.ApiModels.HealthScore;
import edu.dbinsight.api.ApiModels.QueryTelemetry;
import edu.dbinsight.persistence.AlertingPersistenceService;
import edu.dbinsight.persistence.DatabaseConnectionService;
import edu.dbinsight.persistence.MonitoredDatabaseEntity;
import edu.dbinsight.persistence.MonitoringPersistenceService;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatabaseHealthServiceTest {
    @Test
    void returnsInsufficientDataForDatabaseWithNoTablesAndNoQueryTelemetry() throws Exception {
        DatabaseConnectionService connectionService = mock(DatabaseConnectionService.class);
        HealthScoreService healthScoreService = new HealthScoreService();
        QueryTelemetryCollector telemetryCollector = mock(QueryTelemetryCollector.class);
        MonitoringPersistenceService monitoringPersistenceService = mock(MonitoringPersistenceService.class);
        AlertingPersistenceService alertingPersistenceService = mock(AlertingPersistenceService.class);
        MonitoredDatabaseEntity target = mock(MonitoredDatabaseEntity.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet tables = mock(ResultSet.class);

        when(monitoringPersistenceService.resolveDatabase(7L)).thenReturn(target);
        when(target.getDatabaseName()).thenReturn("empty_db");
        when(target.getDatabaseId()).thenReturn(7L);
        when(connectionService.openConnection(target)).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getTables("empty_db", null, "%", new String[]{"TABLE"})).thenReturn(tables);
        when(tables.next()).thenReturn(false);
        when(telemetryCollector.snapshot(7L)).thenReturn(new QueryTelemetry(
                List.of(), "empty_db", false, "No statement digests.", Instant.now(), 500, 0, false, false));
        when(monitoringPersistenceService.saveHealthSnapshot(anyLong(), any(HealthScore.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        DatabaseHealthService service = new DatabaseHealthService(connectionService, healthScoreService,
                telemetryCollector, monitoringPersistenceService, alertingPersistenceService, 500);

        HealthScore result = service.calculate(7L);

        assertEquals(0, result.tableCount());
        assertEquals("INSUFFICIENT_DATA", result.status());
        assertNull(result.queryScore());
        verify(monitoringPersistenceService).saveHealthSnapshot(anyLong(), any(HealthScore.class));
    }
}
