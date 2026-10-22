package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.insurance.Insurer.ARTEA;
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.ContactDetails;
import ee.tuleva.onboarding.epis.ContactDetailsService;
import ee.tuleva.onboarding.error.NotFoundException;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class InsuranceTransferFormService {

  private final InsuranceTransferFormRepository repository;
  private final UserService userService;
  private final ContactDetailsService contactDetailsService;
  private final InsurerForms insurerForms;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public InsuranceTransferForm create(
      AuthenticatedPerson person, CreateInsuranceTransferFormCommand command) {
    final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
    requireActingAsSelf(person);
    Instant now = clock.instant();
    LocalDate today = LocalDate.ofInstant(now, ESTONIAN_ZONE);
    requireEndDateNotPassed(command.endDate(), today);
    requireIdentityDocumentValid(command, today);
    User user = userService.getByIdOrThrow(person.getUserIdOrThrow());
    String pensionAccountNumber = pensionAccountNumber(person);
    var application =
        new InsuranceTransferApplication(applicant(user, pensionAccountNumber), command, today);
    byte[] filledPdf = insurerForms.fill(application);
    return repository.save(
        new InsuranceTransferForm(
            person.getUserIdOrThrow(), command.insurer(), pensionAccountNumber, filledPdf, now));
  }

  public InsuranceTransferForm get(Long id, AuthenticatedPerson person) {
    requireActingAsSelf(person);
    return repository
        .findByIdAndUserId(id, person.getUserIdOrThrow())
        .orElseThrow(() -> notFound(id));
  }

  public InsuranceTransferFormSummary summary(Long id, AuthenticatedPerson person) {
    requireActingAsSelf(person);
    return repository
        .findSummaryByIdAndUserId(id, person.getUserIdOrThrow())
        .orElseThrow(() -> notFound(id));
  }

  static byte[] filledPdfOf(InsuranceTransferForm form) {
    byte[] filledPdf = form.getFilledPdf();
    if (filledPdf == null) {
      throw new NotFoundException(
          "Insurance transfer form document expired: id=" + form.getIdOrThrow());
    }
    return filledPdf;
  }

  static byte[] signedContainerOf(InsuranceTransferForm form) {
    byte[] signedContainer = form.getSignedContainer();
    if (signedContainer == null) {
      throw new NotFoundException(
          "Insurance transfer form has no signed file: id=" + form.getIdOrThrow());
    }
    return signedContainer;
  }

  @Transactional
  public void sign(Long id, byte[] signedContainer, Locale locale) {
    InsuranceTransferForm form = repository.findForUpdateById(id).orElseThrow(() -> notFound(id));
    if (form.isSigned()) {
      return;
    }
    form.sign(signedContainer, clock.instant());
    eventPublisher.publishEvent(new InsuranceTransferFormSigned(id, locale));
  }

  @Transactional(propagation = REQUIRES_NEW)
  public void recordEmailStatus(Long id, InsuranceTransferEmailStatus status) {
    repository.findById(id).orElseThrow(() -> notFound(id)).recordEmailStatus(status);
  }

  private String pensionAccountNumber(AuthenticatedPerson person) {
    ContactDetails contactDetails = contactDetailsService.getContactDetails(person);
    if (!contactDetails.hasPensionAccount()) {
      throw new PensionAccountNotReadyException();
    }
    return contactDetails.getPensionAccountNumber();
  }

  private static Applicant applicant(User user, String pensionAccountNumber) {
    return new Applicant(
        user.getFirstName(),
        user.getLastName(),
        user.getPersonalCode(),
        user.getEmail(),
        user.getPhoneNumber(),
        pensionAccountNumber);
  }

  private static void requireEndDateNotPassed(@Nullable LocalDate endDate, LocalDate today) {
    if (endDate != null && endDate.isBefore(today)) {
      throw new EndDateInThePastException(endDate, today);
    }
  }

  private static void requireIdentityDocumentValid(
      CreateInsuranceTransferFormCommand command, LocalDate today) {
    if (command.insurer() == ARTEA) {
      command.questionnaire().idDocument().requireValidOn(today);
    }
  }

  private static NotFoundException notFound(Long id) {
    return new NotFoundException("Insurance transfer form not found: id=" + id);
  }

  private static void requireActingAsSelf(AuthenticatedPerson person) {
    if (!person.isActingAsSelf()) {
      throw new NotActingAsSelfException();
    }
  }
}
