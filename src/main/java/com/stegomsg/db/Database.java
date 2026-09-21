package com.stegomsg.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

/**
 * PostgreSQL/Supabase database gateway used by all repositories.
 *
 * The application connects to Supabase's shared session pooler. Credentials are read
 * from environment variables / JVM system properties instead of being committed to the
 * source tree. The database schema is PostgreSQL-native and is initialized idempotently
 * from /schema_postgres.sql on startup.
 */
public final class Database {

    private static final String DEFAULT_JDBC_URL =
            "jdbc:postgresql://aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres?sslmode=require";
    private static final String DEFAULT_USERNAME = "postgres.pgqpfiedfwbblgdxhlcm";

    private final String jdbcUrl;
    private final String username;
    private final String password;

    /** Creates the application database connection from environment/system settings. */
    public Database() {
        this(
                setting("SUPABASE_DB_URL", "stegomsg.db.url", DEFAULT_JDBC_URL),
                setting("SUPABASE_DB_USER", "stegomsg.db.user", DEFAULT_USERNAME),
                requiredSecret("SUPABASE_DB_PASSWORD", "stegomsg.db.password")
        );
    }

    /** Visible for tests or another explicitly configured PostgreSQL environment. */
    public Database(String jdbcUrl, String username, String password) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("Database JDBC URL is required.");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Database username is required.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Database password is required.");
        }
        this.jdbcUrl = normalizeJdbcUrl(jdbcUrl);
        this.username = username;
        this.password = password;
    }

    public Connection connect() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        properties.setProperty("sslmode", "require");
        properties.setProperty("ApplicationName", "secure-stego-messenger");
        return DriverManager.getConnection(jdbcUrl, properties);
    }

    /**
     * Initializes the PostgreSQL schema. The SQL file is deliberately idempotent because
     * this method runs every time the JavaFX application starts.
     */
    public void initializeSchema() {
        String schema = readResource("/schema_postgres.sql");

        // Remove comments before the simple statement splitter below. The schema contains
        // no function bodies, so semicolon-delimited execution is sufficient here.
        schema = schema.replaceAll("(?m)--.*$", "");

        try (Connection conn = connect();
             java.sql.Statement stmt = conn.createStatement()) {

            for (String sql : schema.split(";")) {
                String statement = sql.trim();
                if (!statement.isEmpty()) {
                    stmt.executeUpdate(statement);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to initialize Supabase/PostgreSQL schema: " + e.getMessage(), e
            );
        }
    }

    /** Binds one of the application's UUID string ids as a PostgreSQL uuid value. */
    static void setUuid(PreparedStatement ps, int index, String id) throws SQLException {
        if (id == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            try {
                ps.setObject(index, UUID.fromString(id));
            } catch (IllegalArgumentException e) {
                throw new SQLException("Invalid UUID value supplied to database", e);
            }
        }
    }

    /** Reads a PostgreSQL uuid column back as the String representation used by the domain model. */
    static String getUuid(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException e) {
            throw new SQLException("Invalid UUID value returned by database for " + column, e);
        }
    }

    /** Binds an Instant to a PostgreSQL TIMESTAMPTZ column without losing the absolute instant. */
    static void setInstant(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setTimestamp(index, Timestamp.from(value));
        }
    }

    /** Reads a PostgreSQL TIMESTAMPTZ value using the JDBC Timestamp representation. */
    static Instant getInstant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String normalizeJdbcUrl(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("jdbc:postgresql://")) {
            return trimmed;
        }
        if (trimmed.startsWith("postgresql://")) {
            return "jdbc:" + trimmed;
        }
        throw new IllegalArgumentException("SUPABASE_DB_URL must be a PostgreSQL JDBC URL.");
    }

    private static String setting(String environmentName, String systemPropertyName, String fallback) {
        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue.trim();
        }

        String systemValue = System.getProperty(systemPropertyName);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue.trim();
        }

        return fallback;
    }

    private static String requiredSecret(String environmentName, String systemPropertyName) {
        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }

        String systemValue = System.getProperty(systemPropertyName);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }

        throw new IllegalStateException(
                "Supabase database password is not configured. Set " + environmentName
                        + " as an environment variable or -D" + systemPropertyName + " for the JVM."
        );
    }

    private String readResource(String path) {
        try (InputStream in = Database.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Schema resource not found on classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
