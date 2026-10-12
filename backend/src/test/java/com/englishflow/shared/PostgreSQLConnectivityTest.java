package com.englishflow.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PostgreSQLConnectivityTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Verify live PostgreSQL connection and database identity")
    void testDatabaseConnectivity() throws Exception {
        assertThat(dataSource).isNotNull();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            assertThat(connection.isValid(2)).isTrue();

            String dbProductName = connection.getMetaData().getDatabaseProductName();
            assertThat(dbProductName).isEqualTo("PostgreSQL");

            int majorVersion = connection.getMetaData().getDatabaseMajorVersion();
            assertThat(majorVersion).isEqualTo(16);

            try (ResultSet rs = statement.executeQuery("SELECT current_user, current_database()")) {
                assertThat(rs.next()).isTrue();
                String currentUser = rs.getString(1);
                String currentDatabase = rs.getString(2);

                assertThat(currentUser).isEqualTo("englishflow");
                assertThat(currentDatabase).isEqualTo("englishflow");
            }
        }
    }

    @Test
    @DisplayName("Verify that no premature business tables exist before Flyway migrations")
    void testNoBusinessTablesExist() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT count(*) FROM information_schema.tables " +
                     "WHERE table_schema = 'public' AND table_name != 'flyway_schema_history'")) {

            assertThat(rs.next()).isTrue();
            int userTableCount = rs.getInt(1);
            assertThat(userTableCount).isZero();
        }
    }
}
