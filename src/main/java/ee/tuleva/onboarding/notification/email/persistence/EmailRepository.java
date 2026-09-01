package ee.tuleva.onboarding.notification.email.persistence;

import ee.tuleva.onboarding.notification.email.Email;
import ee.tuleva.onboarding.notification.email.EmailStatus;
import ee.tuleva.onboarding.notification.email.EmailType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.repository.CrudRepository;

public interface EmailRepository extends CrudRepository<Email, Long> {

  List<Email> findAllByMandateId(Long mandateId);

  List<Email> findAllByMandateBatchId(Long mandateBatchId);

  List<Email> findAllByPersonalCodeAndTypeAndStatusInOrderByCreatedDateDescIdDesc(
      String personalCode, EmailType type, Collection<EmailStatus> statuses);

  Optional<Email> findFirstByPersonalCodeAndTypeAndMandateIdAndStatusInOrderByCreatedDateDescIdDesc(
      String personalCode,
      EmailType type,
      @Nullable Long mandateId,
      Collection<EmailStatus> statuses);

  Optional<Email>
      findFirstByPersonalCodeAndTypeAndMandateBatchIdAndStatusInOrderByCreatedDateDescIdDesc(
          String personalCode,
          EmailType type,
          Long mandateBatchId,
          Collection<EmailStatus> statuses);

  Optional<Email> findFirstByPersonalCodeAndTypeOrderByCreatedDateDescIdDesc(
      String personalCode, EmailType type);

  boolean existsByType(EmailType type);

  Optional<Email> findByMandrillMessageId(String mandrillMessageId);

  boolean existsByMailchimpCampaign(String mailchimpCampaign);
}
