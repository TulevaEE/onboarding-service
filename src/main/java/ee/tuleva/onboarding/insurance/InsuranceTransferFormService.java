package ee.tuleva.onboarding.insurance;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
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
    User user = userService.getByIdOrThrow(person.getUserIdOrThrow());
    String pensionAccountNumber = pensionAccountNumber(person);
    Instant now = clock.instant();
    var application =
        new InsuranceTransferApplication(
            applicant(user, pensionAccountNumber),
            command,
            LocalDate.ofInstant(now, ESTONIAN_ZONE));
    byte[] filledPdf = insurerForms.fill(application);
    return repository.save(
        new InsuranceTransferForm(
            person.getUserIdOrThrow(), command.insurer(), pensionAccountNumber, filledPdf, now));
  }

  public InsuranceTransferForm get(Long id, AuthenticatedPerson person) {
    requireActingAsSelf(person);
    return repository
        .findByIdAndUserId(id, person.getUserIdOrThrow())
        .orElseThrow(() -> new NotFoundException("Insurance transfer form not found: id=" + id));
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
    InsuranceTransferForm form =
        repository
            .findForUpdateById(id)
            .orElseThrow(
                () -> new NotFoundException("Insurance transfer form not found: id=" + id));
    if (form.isSigned()) {
      return;
    }
    form.sign(signedContainer, clock.instant());
    eventPublisher.publishEvent(new InsuranceTransferFormSigned(id, locale));
  }

  @Transactional(propagation = REQUIRES_NEW)
  public void recordEmailStatus(Long id, InsuranceTransferEmailStatus status) {
    repository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Insurance transfer form not found: id=" + id))
        .recordEmailStatus(status);
  }

  private String pensionAccountNumber(AuthenticatedPerson person) {
    String pensionAccountNumber =
        contactDetailsService.getContactDetails(person).getPensionAccountNumber();
    if (pensionAccountNumber == null || pensionAccountNumber.isBlank()) {
      throw new PensionAccountNotReadyException();
    }
    return pensionAccountNumber;
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

  private static void requireActingAsSelf(AuthenticatedPerson person) {
    if (!person.isActingAsSelf()) {
      throw new NotActingAsSelfException();
    }
  }
}
