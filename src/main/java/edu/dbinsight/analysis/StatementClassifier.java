package edu.dbinsight.analysis;

import java.util.List;
import java.util.Locale;

final class StatementClassifier {
    private static final List<String> DBINSIGHT_TABLES = List.of(
            "app_user", "monitored_database", "table_metadata", "column_metadata", "index_metadata",
            "index_column", "monitored_query", "query_performance", "schema_issue", "health_score",
            "recommendation");

    private StatementClassifier() {}

    static boolean isInternal(String statement) {
        if (statement == null || statement.isBlank()) {
            return true;
        }
        String normalized = statement.stripLeading().toUpperCase(Locale.ROOT);
        if (normalized.contains("INFORMATION_SCHEMA")
                || normalized.contains("PERFORMANCE_SCHEMA")
                || normalized.contains("FROM `SYS`.")
                || isColumnMetadataLookup(normalized)
                || normalized.startsWith("SHOW ")
                || normalized.startsWith("SET ")
                || normalized.startsWith("SELECT @@")
                || normalized.startsWith("SELECT SCHEMA (")) {
            return true;
        }
        if (normalized.startsWith("CREATE TABLE IF NOT EXISTS")) {
            return DBINSIGHT_TABLES.stream().anyMatch(table -> normalized.contains("`" + table.toUpperCase(Locale.ROOT) + "`"));
        }
        return false;
    }

    private static boolean isColumnMetadataLookup(String normalized) {
        return normalized.startsWith("SELECT ")
                && normalized.contains("TABLE_SCHEMA")
                && normalized.contains("COLUMN_NAME")
                && normalized.contains("DATA_TYPE")
                && normalized.contains("COLUMN_TYPE");
    }
}