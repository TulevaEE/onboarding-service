package ee.tuleva.onboarding.user.contact;

import ee.tuleva.onboarding.user.ContactChangeSource;
import ee.tuleva.onboarding.user.ContactField;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

public record ContactChangeRows(JdbcClient jdbcClient) {

  public record Row(
      ContactField field,
      @Nullable String oldValue,
      @Nullable String newValue,
      ContactChangeSource source,
      Instant changedAt) {}

  public List<Row> of(Long userId) {
    return jdbcClient
        .sql(
            """
            SELECT field, old_value, new_value, source, changed_at
            FROM user_contact_change
            WHERE user_id = :userId
            ORDER BY id
            """)
        .param("userId", userId)
        .query(
            (rs, rowNum) ->
                new Row(
                    ContactField.valueOf(rs.getString("field")),
                    rs.getString("old_value"),
                    rs.getString("new_value"),
                    ContactChangeSource.valueOf(rs.getString("source")),
                    rs.getTimestamp("changed_at").toInstant()))
        .list();
  }
}
