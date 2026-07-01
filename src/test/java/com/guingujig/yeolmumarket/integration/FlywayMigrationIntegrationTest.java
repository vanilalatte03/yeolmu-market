package com.guingujig.yeolmumarket.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationIntegrationTest {

  @Container
  static final MySQLContainer mysql =
      new MySQLContainer(DockerImageName.parse("mysql:8.4"))
          .withDatabaseName("yeolmu_market")
          .withUsername("yeolmu")
          .withPassword("local-password");

  @Test
  void 빈_MySQL_DB는_V1부터_최신_migration까지_적용된다() throws SQLException {
    Flyway flyway =
        Flyway.configure()
            .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
            .locations("classpath:db/migration")
            .baselineOnMigrate(false)
            .baselineVersion("1")
            .validateOnMigrate(true)
            .load();

    flyway.migrate();

    try (Connection connection =
        DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
      assertThat(appliedVersions(connection))
          .containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
      assertThat(tableExists(connection, "users")).isTrue();
      assertThat(tableExists(connection, "product_image")).isTrue();
      assertThat(tableExists(connection, "review")).isTrue();
      assertThat(tableExists(connection, "refund_request")).isTrue();
      assertThat(columnExists(connection, "wish", "created_at")).isTrue();
      assertThat(columnExists(connection, "orders", "tracking_number")).isTrue();
      assertThat(columnExists(connection, "chatmessage", "accepted_message_id")).isTrue();
      assertThat(columnExists(connection, "product_image", "thumbnail_product_id")).isTrue();
      assertThat(indexExists(connection, "product", "idx_product_public_list_latest")).isTrue();
    }
  }

  private List<String> appliedVersions(Connection connection) throws SQLException {
    String sql =
        """
        select version
        from flyway_schema_history
        where success = 1
        order by installed_rank
        """;
    List<String> versions = new ArrayList<>();
    try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
      while (resultSet.next()) {
        versions.add(resultSet.getString("version"));
      }
    }
    return versions;
  }

  private boolean tableExists(Connection connection, String tableName) throws SQLException {
    String sql =
        """
        select count(*)
        from information_schema.tables
        where table_schema = database()
          and table_name = ?
        """;
    try (var statement = connection.prepareStatement(sql)) {
      statement.setString(1, tableName);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getInt(1) == 1;
      }
    }
  }

  private boolean columnExists(Connection connection, String tableName, String columnName)
      throws SQLException {
    String sql =
        """
        select count(*)
        from information_schema.columns
        where table_schema = database()
          and table_name = ?
          and column_name = ?
        """;
    try (var statement = connection.prepareStatement(sql)) {
      statement.setString(1, tableName);
      statement.setString(2, columnName);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getInt(1) == 1;
      }
    }
  }

  private boolean indexExists(Connection connection, String tableName, String indexName)
      throws SQLException {
    String sql =
        """
        select count(*)
        from information_schema.statistics
        where table_schema = database()
          and table_name = ?
          and index_name = ?
        """;
    try (var statement = connection.prepareStatement(sql)) {
      statement.setString(1, tableName);
      statement.setString(2, indexName);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getInt(1) > 0;
      }
    }
  }
}
