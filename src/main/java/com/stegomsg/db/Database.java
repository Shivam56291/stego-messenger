package com.stegomsg.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {

    private final String jdbcUrl;

    public Database(Path databaseFile) {
        try {
            Path absolutePath = databaseFile.toAbsolutePath();
            Path parent = absolutePath.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            this.jdbcUrl = "jdbc:sqlite:" + absolutePath;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to create database directory", e
            );
        }
    }

    public Connection connect() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }

        return conn;
    }

    public void initializeSchema() {
        String schema = readResource("/schema_sqlite.sql");

        schema = schema.replaceAll("(?m)--.*$", "");

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {

            for (String sql : schema.split(";")) {
                String statement = sql.trim();

                if (!statement.isEmpty()) {
                    stmt.executeUpdate(statement);
                }
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to initialize database schema: "
                            + e.getMessage(),
                    e
            );
        }
    }

    private String readResource(String path) {
        try (InputStream in = Database.class.getResourceAsStream(path)) {

            if (in == null) {
                throw new IllegalStateException(
                        "Schema resource not found on classpath: " + path
                );
            }

            return new String(
                    in.readAllBytes(),
                    StandardCharsets.UTF_8
            );

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
