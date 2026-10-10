package edu.dbinsight.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StatementClassifierTest {
    @Test
    void classifiesKnownMetadataAndBootstrapDigestsAsInternal() {
        assertTrue(StatementClassifier.isInternal(
                "SELECT * FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = ?"));
        assertTrue(StatementClassifier.isInternal(
                "SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, CASE WHEN UPPER(DATA_TYPE) = ? "
                        + "THEN IF(LOCATE(?, UPPER(COLUMN_TYPE)) > 0, ?, ?)"));
        assertTrue(StatementClassifier.isInternal("SHOW FULL FIELDS FROM dbinsight.app_user"));
        assertTrue(StatementClassifier.isInternal("SELECT @@SESSION.transaction_read_only"));
        assertTrue(StatementClassifier.isInternal("CREATE TABLE IF NOT EXISTS `app_user` (... )"));
    }

    @Test
    void preservesOrdinaryApplicationQueriesAsUserWorkload() {
        assertFalse(StatementClassifier.isInternal("SELECT * FROM `app_user` LIMIT ?, ..."));
        assertFalse(StatementClassifier.isInternal("UPDATE orders SET status = ? WHERE id = ?"));
    }
}