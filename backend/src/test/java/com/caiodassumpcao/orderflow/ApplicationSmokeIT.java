package com.caiodassumpcao.orderflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ApplicationSmokeIT {

  @Autowired private JdbcTemplate jdbc;

  @Test
  void contextStartsAndFlywayCreatesCqrsSchemas() {
    List<String> schemas =
        jdbc.queryForList(
            "SELECT schema_name FROM information_schema.schemata"
                + " WHERE schema_name IN ('command', 'query') ORDER BY schema_name",
            String.class);

    assertThat(schemas).containsExactly("command", "query");
  }

  @Test
  void flywayHistoryRecordsBaselineMigrationAsSuccessful() {
    Boolean success =
        jdbc.queryForObject(
            "SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);

    assertThat(success).isTrue();
  }
}
