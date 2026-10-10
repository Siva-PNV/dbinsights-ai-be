package edu.dbinsight.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DatabaseRegistrationPolicyTest {
    @Test
    void acceptsValidatedMysqlRegistration() {
        assertDoesNotThrow(() -> DatabaseRegistrationPolicy.validate(
                "analytics_stage",
                "MYSQL",
                "127.0.0.1",
                3306
        ));
    }

    @Test
    void rejectsUnsafeHostnames() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> DatabaseRegistrationPolicy.validate("evil", "MYSQL", "not a valid host", 3306));
        assert exception.getMessage().contains("host");
    }

    @Test
    void rejectsDatabaseEnginesWithoutAConnector() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> DatabaseRegistrationPolicy.validate("analytics", "POSTGRESQL", "localhost", 5432));
        assert exception.getMessage().contains("MySQL");
    }
}
