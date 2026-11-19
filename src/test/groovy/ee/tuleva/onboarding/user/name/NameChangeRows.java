package ee.tuleva.onboarding.user.name;

import ee.tuleva.onboarding.user.NameChangeSource;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

public record NameChangeRows(JdbcClient jdbcClient) {

  public record Row(
      String oldFirstName,
      String oldLastName,
      String newFirstName,
      String newLastName,
      NameChangeSource source,
      @Nullable String documentReference,
      Instant changedAt) {}

  public List<Row> of(Long userId) {
    return jdbcClient
        .sql(
            """
            SELECT old_first_name, old_last_name, new_first_name, new_last_name, source,
                   document_reference, changed_at
            FROM user_name_change
            WHERE user_id = :userId
            ORDER BY id
            """)
        .param("userId", userId)
        .query(
            (rs, rowNum) ->
                new Row(
                    rs.getString("old_first_name"),
                    rs.getString("old_last_name"),
                    rs.getString("new_first_name"),
                    rs.getString("new_last_name"),
                    NameChangeSource.valueOf(rs.getString("source")),
                    rs.getString("document_reference"),
                    rs.getTimestamp("changed_at").toInstant()))
        .list();
  }

  public void deleteOf(Long userId) {
    jdbcClient
        .sql("DELETE FROM user_name_change WHERE user_id = :userId")
        .param("userId", userId)
        .update();
  }
}
