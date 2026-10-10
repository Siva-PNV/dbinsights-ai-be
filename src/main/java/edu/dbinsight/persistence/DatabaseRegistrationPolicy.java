package edu.dbinsight.persistence;

import java.util.Locale;
import java.util.regex.Pattern;

public final class DatabaseRegistrationPolicy {
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_-]{2,64}$");
    private static final Pattern VALID_HOST = Pattern.compile("^(localhost|(([0-9]{1,3}\\.){3}[0-9]{1,3})|([A-Za-z0-9.-]+))$");

    private DatabaseRegistrationPolicy() {
    }

    public static Registration validate(String databaseName, String dbType, String host, Integer port) {
        String normalizedName = normalizeDatabaseName(databaseName);
        String normalizedType = normalizeDatabaseType(dbType);
        String normalizedHost = normalizeHost(host);
        int normalizedPort = normalizePort(port);
        return new Registration(normalizedName, normalizedType, normalizedHost, normalizedPort);
    }

    public record Registration(String databaseName, String dbType, String host, int port) {}

    private static String normalizeDatabaseName(String databaseName) {
        if (databaseName == null || databaseName.isBlank()) {
            throw new IllegalArgumentException("Database name is required.");
        }
        String normalized = databaseName.trim();
        if (!VALID_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Database name must contain 2-64 letters, digits, underscores or dashes.");
        }
        return normalized;
    }

    private static String normalizeDatabaseType(String dbType) {
        String normalized = dbType == null ? "MYSQL" : dbType.trim().toUpperCase(Locale.ROOT);
        if (!"MYSQL".equals(normalized)) {
            throw new IllegalArgumentException("Only MySQL database targets are currently supported.");
        }
        return normalized;
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host is required.");
        }
        String normalized = host.trim();
        if (!VALID_HOST.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Host must be a valid hostname, localhost, or IPv4 address.");
        }
        return normalized;
    }

    private static int normalizePort(Integer port) {
        int normalized = port == null ? 3306 : port;
        if (normalized < 1 || normalized > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535.");
        }
        return normalized;
    }
}
