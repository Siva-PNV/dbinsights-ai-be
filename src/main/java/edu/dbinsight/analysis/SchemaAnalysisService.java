package edu.dbinsight.analysis;

import edu.dbinsight.api.ApiModels.IssueSummary;
import edu.dbinsight.api.ApiModels.LiveColumn;
import edu.dbinsight.api.ApiModels.LiveForeignKey;
import edu.dbinsight.api.ApiModels.LiveIndex;
import edu.dbinsight.api.ApiModels.LiveSchemaSummary;
import edu.dbinsight.api.ApiModels.LiveSchemaTable;
import edu.dbinsight.persistence.DatabaseConnectionService;
import edu.dbinsight.persistence.MonitoredDatabaseEntity;
import edu.dbinsight.persistence.MonitoringPersistenceService;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

@Service
public class SchemaAnalysisService {
    private final DatabaseConnectionService databaseConnectionService;
    private final MonitoringPersistenceService monitoringPersistenceService;

    public SchemaAnalysisService(DatabaseConnectionService databaseConnectionService,
                                 MonitoringPersistenceService monitoringPersistenceService) {
        this.databaseConnectionService = databaseConnectionService;
        this.monitoringPersistenceService = monitoringPersistenceService;
    }

    public LiveSchemaSummary analyze() {
        return analyze(null);
    }

    public LiveSchemaSummary analyze(Long databaseId) {
        MonitoredDatabaseEntity target = monitoringPersistenceService.resolveDatabase(databaseId);
        try (Connection connection = databaseConnectionService.openConnection(target)) {
            String databaseName = target.getDatabaseName();
            DatabaseMetaData metadata = connection.getMetaData();
            List<String> tableNames = readTableNames(metadata, databaseName);
            List<LiveSchemaTable> tables = new ArrayList<>();
            List<IssueSummary> issues = new ArrayList<>();
            long issueId = 1;

            for (String tableName : tableNames) {
                List<LiveColumn> columns = readColumns(metadata, databaseName, tableName);
                List<String> primaryKeyColumns = readPrimaryKeys(metadata, databaseName, tableName);
                List<LiveForeignKey> foreignKeys = readForeignKeys(metadata, databaseName, tableName);
                List<LiveIndex> indexes = readIndexes(metadata, databaseName, tableName);

                tables.add(new LiveSchemaTable(tableName, columns, primaryKeyColumns, foreignKeys, indexes));

                if (primaryKeyColumns.isEmpty()) {
                    issues.add(new IssueSummary(issueId++, tableName, "MISSING_PRIMARY_KEY",
                            "No primary key was reported for this table by JDBC metadata.", "MEDIUM", "OPEN"));
                }
                for (ForeignKeyGroup foreignKey : groupForeignKeys(foreignKeys)) {
                    if (!hasSupportingIndex(foreignKey.localColumns(), indexes)) {
                        issues.add(new IssueSummary(issueId++, tableName, "FOREIGN_KEY_WITHOUT_INDEX",
                                "Foreign key " + foreignKey.name() + " references " + foreignKey.referencedTable()
                                        + " but its local columns are not the leading columns of an index.",
                                "MEDIUM", "OPEN"));
                    }
                }
            }

            return new LiveSchemaSummary(databaseName, List.copyOf(tables), List.copyOf(issues), Instant.now(), false);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to collect live schema metadata for the selected database.", exception);
        }
    }

    private List<String> readTableNames(DatabaseMetaData metadata, String databaseName) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (ResultSet result = metadata.getTables(databaseName, null, "%", new String[]{"TABLE"})) {
            while (result.next()) {
                tables.add(result.getString("TABLE_NAME"));
            }
        }
        tables.sort(String::compareToIgnoreCase);
        return tables;
    }

    private List<LiveColumn> readColumns(DatabaseMetaData metadata, String databaseName, String tableName)
            throws SQLException {
        List<LiveColumn> columns = new ArrayList<>();
        try (ResultSet result = metadata.getColumns(databaseName, null, tableName, "%")) {
            while (result.next()) {
                columns.add(new LiveColumn(result.getString("COLUMN_NAME"), result.getString("TYPE_NAME"),
                        result.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                        result.getInt("ORDINAL_POSITION")));
            }
        }
        columns.sort(Comparator.comparingInt(LiveColumn::ordinalPosition));
        return List.copyOf(columns);
    }

    private List<String> readPrimaryKeys(DatabaseMetaData metadata, String databaseName, String tableName)
            throws SQLException {
        Map<Short, String> keys = new TreeMap<>();
        try (ResultSet result = metadata.getPrimaryKeys(databaseName, null, tableName)) {
            while (result.next()) {
                keys.put(result.getShort("KEY_SEQ"), result.getString("COLUMN_NAME"));
            }
        }
        return List.copyOf(keys.values());
    }

    private List<LiveForeignKey> readForeignKeys(DatabaseMetaData metadata, String databaseName, String tableName)
            throws SQLException {
        List<LiveForeignKey> keys = new ArrayList<>();
        try (ResultSet result = metadata.getImportedKeys(databaseName, null, tableName)) {
            while (result.next()) {
                String constraintName = result.getString("FK_NAME");
                if (constraintName == null || constraintName.isBlank()) {
                    constraintName = result.getString("PKTABLE_NAME") + "_" + result.getString("PK_NAME");
                }
                keys.add(new LiveForeignKey(constraintName, result.getString("FKCOLUMN_NAME"),
                        result.getString("PKTABLE_NAME"), result.getString("PKCOLUMN_NAME"),
                        result.getInt("KEY_SEQ")));
            }
        }
        keys.sort(Comparator.comparing(LiveForeignKey::constraintName).thenComparingInt(LiveForeignKey::sequence));
        return List.copyOf(keys);
    }

    private List<LiveIndex> readIndexes(DatabaseMetaData metadata, String databaseName, String tableName)
            throws SQLException {
        Map<String, IndexAccumulator> indexes = new LinkedHashMap<>();
        try (ResultSet result = metadata.getIndexInfo(databaseName, null, tableName, false, false)) {
            while (result.next()) {
                String indexName = result.getString("INDEX_NAME");
                String columnName = result.getString("COLUMN_NAME");
                if (indexName == null || columnName == null) {
                    continue;
                }
                IndexAccumulator index = indexes.computeIfAbsent(indexName,
                        ignored -> new IndexAccumulator(indexName, !resultBoolean(result, "NON_UNIQUE", true)));
                index.columns.put(result.getShort("ORDINAL_POSITION"), columnName);
            }
        }

        return indexes.values().stream()
                .map(index -> new LiveIndex(index.name, index.unique, List.copyOf(index.columns.values())))
                .toList();
    }

    private boolean resultBoolean(ResultSet result, String column, boolean fallback) {
        try {
            return result.getBoolean(column);
        } catch (SQLException ignored) {
            return fallback;
        }
    }

    private List<ForeignKeyGroup> groupForeignKeys(List<LiveForeignKey> keys) {
        Map<String, List<LiveForeignKey>> groups = new LinkedHashMap<>();
        for (LiveForeignKey key : keys) {
            groups.computeIfAbsent(key.constraintName() + "->" + key.referencedTable(), ignored -> new ArrayList<>())
                    .add(key);
        }
        return groups.values().stream().map(group -> {
            group.sort(Comparator.comparingInt(LiveForeignKey::sequence));
            LiveForeignKey first = group.get(0);
            return new ForeignKeyGroup(first.constraintName(), first.referencedTable(),
                    group.stream().map(LiveForeignKey::columnName).toList());
        }).toList();
    }

    static boolean hasSupportingIndex(List<String> foreignKeyColumns, List<LiveIndex> indexes) {
        return indexes.stream().anyMatch(index -> index.columns().size() >= foreignKeyColumns.size()
                && index.columns().subList(0, foreignKeyColumns.size()).equals(foreignKeyColumns));
    }

    private static final class IndexAccumulator {
        private final String name;
        private final boolean unique;
        private final Map<Short, String> columns = new TreeMap<>();

        private IndexAccumulator(String name, boolean unique) {
            this.name = name;
            this.unique = unique;
        }
    }

    private record ForeignKeyGroup(String name, String referencedTable, List<String> localColumns) {}
}