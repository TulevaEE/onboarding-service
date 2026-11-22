package ee.tuleva.onboarding.user.contact;

import static ee.tuleva.onboarding.user.ContactField.EMAIL;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.user.AsciiLetterCase;
import ee.tuleva.onboarding.user.ContactChange;
import ee.tuleva.onboarding.user.ContactChangeSource;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ContactChangeRepository {

  private final JdbcClient jdbcClient;

  public void saveAll(Long userId, ContactChangeSource source, List<ContactChange> changes) {
    changes.forEach(change -> save(userId, source, change));
  }

  public record ReplacedEmail(String address, @Nullable String currentAddress) {

    public boolean isTheCurrentAddress() {
      return AsciiLetterCase.equalIgnoringCase(address, currentAddress);
    }
  }

  public List<ReplacedEmail> emailsReplacedBetween(Long userId, Instant from, Instant until) {
    return jdbcClient
        .sql(
            """
            SELECT replaced.old_value, person.email AS current_email
            FROM user_contact_change replaced
            JOIN users person ON person.id = replaced.user_id
            WHERE replaced.user_id = :userId
              AND replaced.field = :field
              AND replaced.old_value IS NOT NULL
              AND replaced.changed_at >= :from
              AND replaced.changed_at <= :until
            ORDER BY replaced.changed_at DESC, replaced.id DESC
            """)
        .param("userId", userId)
        .param("field", EMAIL.name())
        .param("from", Timestamp.from(from))
        .param("until", Timestamp.from(until))
        .query(
            (rs, rowNum) ->
                new ReplacedEmail(
                    requireNonNull(rs.getString("old_value")), rs.getString("current_email")))
        .list();
  }

  private void save(Long userId, ContactChangeSource source, ContactChange change) {
    jdbcClient
        .sql(
            """
            INSERT INTO user_contact_change
              (user_id, field, old_value, new_value, source, changed_at)
            VALUES (:userId, :field, :oldValue, :newValue, :source, :changedAt)
            """)
        .param("userId", userId)
        .param("field", change.field().name())
        .param("oldValue", change.oldValue(), Types.VARCHAR)
        .param("newValue", change.newValue(), Types.VARCHAR)
        .param("source", source.name())
        .param("changedAt", Timestamp.from(change.changedAt()))
        .update();
  }
}
