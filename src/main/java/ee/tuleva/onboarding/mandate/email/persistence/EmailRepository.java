package ee.tuleva.onboarding.mandate.email.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface EmailRepository extends CrudRepository<Email, Long> {

  List<Email> findAllByUserIdAndTypeAndStatusOrderByCreatedDateDesc(
      long userId, EmailType type, EmailStatus status);

  @Query(
      """
          SELECT e FROM Email e
          WHERE e.userId = :userId AND e.type = :type AND e.status IN (:statuses)
          ORDER BY e.createdDate DESC
  """)
  Optional<Email> findLatestEmail(
      @Param("userId") long userId,
      @Param("type") EmailType type,
      @Param("statuses") EmailStatus[] statuses);
}
