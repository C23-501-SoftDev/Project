package com.knowledgebase.integration;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentSortOrderLiquibaseTest {

    @Test
    void migrationBackfillsOrderIndependentlyForEveryTreeLevel() throws Exception {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE documents (
                        id BIGINT PRIMARY KEY,
                        space_id BIGINT NOT NULL,
                        parent_document_id BIGINT,
                        created_at TIMESTAMP NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO documents (id, space_id, parent_document_id, created_at) VALUES
                        (10, 1, NULL, TIMESTAMP '2026-01-01 10:00:00'),
                        (11, 1, NULL, TIMESTAMP '2026-01-01 11:00:00'),
                        (20, 1, 10, TIMESTAMP '2026-01-01 12:00:00'),
                        (21, 1, 10, TIMESTAMP '2026-01-01 13:00:00'),
                        (30, 2, NULL, TIMESTAMP '2026-01-01 14:00:00')
                    """);

            applyChangeLog(connection, "db/changelog/changes/034-add-document-sort-order.xml");

            assertEquals(0, sortOrder(statement, 10));
            assertEquals(1, sortOrder(statement, 11));
            assertEquals(0, sortOrder(statement, 20));
            assertEquals(1, sortOrder(statement, 21));
            assertEquals(0, sortOrder(statement, 30));

            statement.executeUpdate("""
                    INSERT INTO documents (id, space_id, parent_document_id, created_at)
                    VALUES (31, 2, NULL, CURRENT_TIMESTAMP)
                    """);
            assertEquals(0, sortOrder(statement, 31));
        }
    }

    private Connection openConnection() throws Exception {
        String databaseName = "document_order_liquibase_" + UUID.randomUUID().toString().replace("-", "");
        return DriverManager.getConnection(
                "jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
    }

    private void applyChangeLog(Connection connection, String changeLogPath) throws Exception {
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        Liquibase liquibase = new Liquibase(changeLogPath, new ClassLoaderResourceAccessor(), database);
        liquibase.update(new Contexts(), new LabelExpression());
    }

    private int sortOrder(Statement statement, long documentId) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(
                "SELECT sort_order FROM documents WHERE id = " + documentId)) {
            if (!resultSet.next()) {
                throw new AssertionError("Document not found: " + documentId);
            }
            return resultSet.getInt(1);
        }
    }
}
