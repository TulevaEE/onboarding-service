package ee.tuleva.onboarding.user.contact;

import ee.tuleva.onboarding.user.ContactChange;
import ee.tuleva.onboarding.user.ContactChangeSource;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ContactChangeRepository {

  private final JdbcClient jdbcClient;

  public void saveAll(Long userId, ContactChangeSource source, List<ContactChange> changes) {
    changes.forEach(change -> save(userId, source, change));
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
