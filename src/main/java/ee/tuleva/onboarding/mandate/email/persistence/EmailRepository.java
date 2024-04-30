package ee.tuleva.onboarding.mandate.email.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface EmailRepository extends CrudRepository<Email, Long> {

  List<Email> findAllByPersonalCodeAndTypeAndStatusOrderByCreatedDateDesc(
      String personalCode, EmailType type, EmailStatus status);

  Optional<Email> findFirstByPersonalCodeAndTypeAndStatusInOrderByCreatedDateDesc(
      String personalCode, EmailType type, Collection<EmailStatus> statuses);
}
