package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonLegalEntity;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static ee.tuleva.onboarding.epis.ContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.arteaRequest;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.compensaRequest;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.idCard;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.questionnaire;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.savedForm;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.signedForm;
import static ee.tuleva.onboarding.insurance.Insurer.ARTEA;
import static ee.tuleva.onboarding.insurance.Insurer.COMPENSA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.epis.ContactDetailsService;
import ee.tuleva.onboarding.error.NotFoundException;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InsuranceTransferFormServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-17T22:30:00Z");

  @Mock private InsuranceTransferFormRepository repository;
  @Mock private UserService userService;
  @Mock private ContactDetailsService contactDetailsService;
  @Mock private ApplicationEventPublisher eventPublisher;

  private final User user = sampleUser().build();
  private final AuthenticatedPerson person = authenticatedPersonFromUser(user).build();
  private InsuranceTransferFormService service;

  @BeforeEach
  void setUp() {
    service =
        new InsuranceTransferFormService(
            repository,
            userService,
            contactDetailsService,
            new InsurerForms(),
            eventPublisher,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void fillsTheFormWithThePensionAccountNumberReadNowAndKeepsThatNumberWithTheForm()
      throws IOException {
    given(userService.getByIdOrThrow(user.getId())).willReturn(user);
    var contactDetails = contactDetailsFixture();
    contactDetails.setPensionAccountNumber("99812345678");
    given(contactDetailsService.getContactDetails(person)).willReturn(contactDetails);
    given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

    var form = service.create(person, compensaRequest());

    assertThat(form)
        .extracting(
            InsuranceTransferForm::getUserId,
            InsuranceTransferForm::getInsurer,
            InsuranceTransferForm::getPensionAccountNumber,
            InsuranceTransferForm::getCreatedAt,
            InsuranceTransferForm::isSigned)
        .containsExactly(user.getId(), COMPENSA, "99812345678", NOW, false);
    try (var pdf = Loader.loadPDF(form.getFilledPdf())) {
      assertThat(new PDFTextStripper().getText(pdf))
          .contains(
              user.getFirstName() + " " + user.getLastName(),
              user.getPersonalCode(),
              "99812345678",
              "18.10.2026");
    }
  }

  @Test
  void refusesToFillTheFormWhileThePensionAccountNumberIsNotKnown() {
    given(userService.getByIdOrThrow(user.getId())).willReturn(user);
    var contactDetails = contactDetailsFixture();
    contactDetails.setPensionAccountNumber(null);
    given(contactDetailsService.getContactDetails(person)).willReturn(contactDetails);

    assertThatThrownBy(() -> service.create(person, compensaRequest()))
        .isInstanceOf(PensionAccountNotReadyException.class);
  }

  @Test
  void refusesToEndTheContractFromADateThatHasPassedInEstoniaWithoutAskingThePensionRegistry() {
    var command = compensaRequest();
    var yesterdayInEstonia =
        new CreateInsuranceTransferFormCommand(
            command.insurer(),
            command.policyNumber(),
            LocalDate.of(2026, 10, 17),
            command.address(),
            command.citizenship(),
            command.countryOfBirth(),
            command.taxResidency(),
            command.pep(),
            command.artea());

    assertThatThrownBy(() -> service.create(person, yesterdayInEstonia))
        .isInstanceOf(EndDateInThePastException.class);
    then(contactDetailsService).shouldHaveNoInteractions();
    then(repository).shouldHaveNoInteractions();
  }

  @Test
  void refusesAnIdentityDocumentIssuedAfterTodayWithoutAskingThePensionRegistry() {
    var issuedTomorrow =
        arteaRequest(questionnaire(idCard(LocalDate.of(2026, 10, 19), LocalDate.of(2031, 1, 1))));

    assertThatThrownBy(() -> service.create(person, issuedTomorrow))
        .isInstanceOf(IdentityDocumentIssuedInTheFutureException.class);
    then(contactDetailsService).shouldHaveNoInteractions();
    then(repository).shouldHaveNoInteractions();
  }

  @Test
  void refusesAnIdentityDocumentThatExpiredYesterdayInEstoniaWithoutAskingThePensionRegistry() {
    var expiredYesterdayInEstonia =
        arteaRequest(questionnaire(idCard(LocalDate.of(2021, 1, 1), LocalDate.of(2026, 10, 17))));

    assertThatThrownBy(() -> service.create(person, expiredYesterdayInEstonia))
        .isInstanceOf(IdentityDocumentExpiredException.class);
    then(contactDetailsService).shouldHaveNoInteractions();
    then(repository).shouldHaveNoInteractions();
  }

  @Test
  void acceptsAnIdentityDocumentIssuedTodayInEstoniaAndValidUntilToday() {
    given(userService.getByIdOrThrow(user.getId())).willReturn(user);
    given(contactDetailsService.getContactDetails(person)).willReturn(contactDetailsFixture());
    given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    var issuedAndExpiringTodayInEstonia =
        arteaRequest(questionnaire(idCard(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 18))));

    var form = service.create(person, issuedAndExpiringTodayInEstonia);

    assertThat(form.getInsurer()).isEqualTo(ARTEA);
  }

  @Test
  void refusesAPersonActingForAnotherPersonWithoutAskingThePensionRegistry() {
    var representative =
        authenticatedPersonFromUser(user)
            .role(new Role(PERSON, "60001019906", "Represented person"))
            .build();

    assertThatThrownBy(() -> service.create(representative, compensaRequest()))
        .isInstanceOf(NotActingAsSelfException.class);
    then(contactDetailsService).shouldHaveNoInteractions();
  }

  @Test
  void refusesAPersonActingForACompany() {
    var company = sampleAuthenticatedPersonLegalEntity().build();

    assertThatThrownBy(() -> service.get(1L, company)).isInstanceOf(NotActingAsSelfException.class);
  }

  @Test
  void findsOnlyTheOwnersForm() {
    given(repository.findByIdAndUserId(1L, user.getId())).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(1L, person)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void summarizesOnlyTheOwnersForm() {
    var summary = signedForm(1L, user.getId(), COMPENSA).summary();
    given(repository.findSummaryByIdAndUserId(1L, user.getId())).willReturn(Optional.of(summary));
    given(repository.findSummaryByIdAndUserId(2L, user.getId())).willReturn(Optional.empty());

    assertThat(service.summary(1L, person)).isEqualTo(summary);
    assertThatThrownBy(() -> service.summary(2L, person)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void refusesToSummarizeAFormForAPersonActingForACompany() {
    var company = sampleAuthenticatedPersonLegalEntity().build();

    assertThatThrownBy(() -> service.summary(1L, company))
        .isInstanceOf(NotActingAsSelfException.class);
  }

  @Test
  void signingStoresTheContainerAndPublishesTheEmailIntentForAfterCommit() {
    var form = savedForm(1L, user.getId(), COMPENSA);
    given(repository.findForUpdateById(1L)).willReturn(Optional.of(form));

    service.sign(1L, new byte[] {7}, Locale.ENGLISH);

    assertThat(form.getSignedContainer()).containsExactly(7);
    assertThat(form.getSignedAt()).isEqualTo(NOW);
    then(eventPublisher).should().publishEvent(new InsuranceTransferFormSigned(1L, Locale.ENGLISH));
  }

  @Test
  void signingAFormThatIsAlreadySignedKeepsTheFirstSignatureAndPublishesNoSecondEmail() {
    var form = signedForm(1L, user.getId(), COMPENSA);
    given(repository.findForUpdateById(1L)).willReturn(Optional.of(form));

    service.sign(1L, new byte[] {7}, Locale.ENGLISH);

    assertThat(form.getSignedContainer()).containsExactly(4, 5, 6);
    then(eventPublisher).should(never()).publishEvent(any());
  }

  @Test
  void recordsHowTheEmailEnded() {
    var form = signedForm(1L, user.getId(), COMPENSA);
    given(repository.findById(1L)).willReturn(Optional.of(form));

    service.recordEmailStatus(1L, InsuranceTransferEmailStatus.FAILED);

    assertThat(form.getEmailStatus()).isEqualTo(InsuranceTransferEmailStatus.FAILED);
  }

  @Test
  void aFormWhoseDocumentsHaveExpiredHasNothingToShow() {
    var form = savedForm(1L, user.getId(), COMPENSA);
    ReflectionTestUtils.setField(form, "filledPdf", null);

    assertThatThrownBy(() -> InsuranceTransferFormService.filledPdfOf(form))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> InsuranceTransferFormService.signedContainerOf(form))
        .isInstanceOf(NotFoundException.class);
  }
}
