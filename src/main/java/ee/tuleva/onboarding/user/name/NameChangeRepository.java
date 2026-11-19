package ee.tuleva.onboarding.user.name;

import java.sql.Timestamp;
import java.sql.Types;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NameChangeRepository {

  private final JdbcClient jdbcClient;

  public void save(Long userId, NameChange change) {
    jdbcClient
        .sql(
            """
            INSERT INTO user_name_change
              (user_id, old_first_name, old_last_name, new_first_name, new_last_name, source,
               document_reference, changed_at)
            VALUES (:userId, :oldFirstName, :oldLastName, :newFirstName, :newLastName, :source,
                    :documentReference, :changedAt)
            """)
        .param("userId", userId)
        .param("oldFirstName", change.oldFirstName(), Types.VARCHAR)
        .param("oldLastName", change.oldLastName(), Types.VARCHAR)
        .param("newFirstName", change.newFirstName())
        .param("newLastName", change.newLastName())
        .param("source", change.basis().source().name())
        .param("documentReference", change.basis().documentReference(), Types.VARCHAR)
        .param("changedAt", Timestamp.from(change.changedAt()))
        .update();
  }
}
