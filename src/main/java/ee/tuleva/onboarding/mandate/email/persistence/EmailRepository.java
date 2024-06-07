package ee.tuleva.onboarding.mandate.email.persistence;

import ee.tuleva.onboarding.mandate.Mandate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface EmailRepository extends CrudRepository<Email, Long> {

  List<Email> findAllByPersonalCodeAndTypeAndStatusOrderByCreatedDateDesc(
      String personalCode, EmailType type, EmailStatus status);

  Optional<Email> findFirstByPersonalCodeAndTypeAndMandateAndStatusInOrderByCreatedDateDesc(
      String personalCode, EmailType type, Mandate mandate, Collection<EmailStatus> statuses);

  Optional<Email> findFirstByPersonalCodeAndTypeOrderByCreatedDateDesc(
      String personalCode, EmailType type);
}
