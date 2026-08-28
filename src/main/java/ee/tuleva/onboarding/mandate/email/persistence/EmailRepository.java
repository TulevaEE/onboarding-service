package ee.tuleva.onboarding.mandate.email.persistence;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface EmailRepository extends CrudRepository<Email, Long> {

  List<Email> findAllByMandate(Mandate mandate);

  List<Email> findAllByMandateBatch(MandateBatch mandate);

  List<Email> findAllByPersonalCodeAndTypeAndStatusInOrderByCreatedDateDescIdDesc(
      String personalCode, EmailType type, Collection<EmailStatus> statuses);

  Optional<Email> findFirstByPersonalCodeAndTypeAndMandateAndStatusInOrderByCreatedDateDescIdDesc(
      String personalCode, EmailType type, Mandate mandate, Collection<EmailStatus> statuses);

  Optional<Email>
      findFirstByPersonalCodeAndTypeAndMandateBatchAndStatusInOrderByCreatedDateDescIdDesc(
          String personalCode,
          EmailType type,
          MandateBatch mandateBatch,
          Collection<EmailStatus> statuses);

  Optional<Email> findFirstByPersonalCodeAndTypeOrderByCreatedDateDescIdDesc(
      String personalCode, EmailType type);

  boolean existsByType(EmailType type);

  Optional<Email> findByMandrillMessageId(String mandrillMessageId);

  boolean existsByMailchimpCampaign(String mailchimpCampaign);
}
