package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.auth.mobileid.MobileIDSession.PHONE_NUMBER;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.savedForm;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.signedForm;
import static ee.tuleva.onboarding.insurance.Insurer.COMPENSA;
import static ee.tuleva.onboarding.signature.SignatureStatus.OUTSTANDING_TRANSACTION;
import static ee.tuleva.onboarding.signature.SignatureStatus.SIGNATURE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.signature.FinishIdCardSignCommand;
import ee.tuleva.onboarding.signature.IdCardSignatureResponse;
import ee.tuleva.onboarding.signature.IdCardSignatureSession;
import ee.tuleva.onboarding.signature.IdCardSignatureStatusResponse;
import ee.tuleva.onboarding.signature.IdSessionException;
import ee.tuleva.onboarding.signature.MobileIdSignatureSession;
import ee.tuleva.onboarding.signature.MobileSignatureResponse;
import ee.tuleva.onboarding.signature.MobileSignatureStatusResponse;
import ee.tuleva.onboarding.signature.SignableEntity;
import ee.tuleva.onboarding.signature.SignatureFile;
import ee.tuleva.onboarding.signature.SignatureService;
import ee.tuleva.onboarding.signature.SignatureStateException;
import ee.tuleva.onboarding.signature.SmartIdSignatureSession;
import ee.tuleva.onboarding.signature.StartIdCardSignCommand;
import ee.tuleva.onboarding.user.User;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;

@ExtendWith(MockitoExtension.class)
class InsuranceTransferFormSignatureServiceTest {

  private static final SignableEntity FORM_1 = new SignableEntity("Insurance transfer form", 1L);
  private static final SignableEntity MANDATE_1 = new SignableEntity("Mandate", 1L);
  private static final byte[] SIGNED_CONTAINER = {9, 9};

  @Mock private InsuranceTransferFormService formService;
  @Mock private SignatureService signatureService;
  @Mock private GenericSessionStore sessionStore;

  private final User user = sampleUser().build();
  private final AuthenticatedPerson person =
      authenticatedPersonFromUser(user).attributes(Map.of(PHONE_NUMBER, "+37255555555")).build();
  private InsuranceTransferFormSignatureService service;

  @BeforeEach
  void setUp() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    service =
        new InsuranceTransferFormSignatureService(
            formService, signatureService, sessionStore, new LocaleService());
  }

  @AfterEach
  void resetLocale() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  void startsSmartIdSigningOfTheStoredFilledFormAndBindsTheSessionToThatForm() {
    var form = savedForm(1L, user.getId(), COMPENSA);
    given(formService.get(1L, person)).willReturn(form);
    var session = smartIdSession();
    given(signatureService.startSmartIdSign(eq(FORM_1), argThat(isTheFilledForm(form)), eq(person)))
        .willReturn(session);

    MobileSignatureResponse response = service.startSmartIdSignature(1L, person);

    assertThat(response.getChallengeCode()).isEqualTo("1234");
    then(sessionStore).should().save(session);
  }

  @Test
  void finishesSmartIdSigningOfTheFormTheSessionWasStartedFor() {
    var session = smartIdSession();
    given(sessionStore.get(SmartIdSignatureSession.class)).willReturn(Optional.of(session));
    given(formService.summary(1L, person)).willReturn(unsignedSummary());
    given(signatureService.getSignedFile(session, FORM_1)).willReturn(SIGNED_CONTAINER);

    MobileSignatureStatusResponse status = service.getSmartIdSignatureStatus(1L, person);

    assertThat(status).isEqualTo(new MobileSignatureStatusResponse(SIGNATURE, "1234"));
    then(formService).should().sign(1L, SIGNED_CONTAINER, Locale.ENGLISH);
  }

  @Test
  void keepsWaitingWhileThePersonHasNotSignedYet() {
    var session = smartIdSession();
    given(sessionStore.get(SmartIdSignatureSession.class)).willReturn(Optional.of(session));
    given(formService.summary(1L, person)).willReturn(unsignedSummary());
    given(signatureService.getSignedFile(session, FORM_1)).willReturn(null);

    MobileSignatureStatusResponse status = service.getSmartIdSignatureStatus(1L, person);

    assertThat(status)
        .isEqualTo(new MobileSignatureStatusResponse(OUTSTANDING_TRANSACTION, "1234"));
    then(formService).should(never()).sign(any(), any(), any());
  }

  @Test
  void aSmartIdSessionStartedForAnotherEntityCannotFinishThisForm() {
    var mandateSession = smartIdSession(MANDATE_1);
    given(sessionStore.get(SmartIdSignatureSession.class)).willReturn(Optional.of(mandateSession));
    given(formService.summary(1L, person)).willReturn(unsignedSummary());
    given(signatureService.getSignedFile(mandateSession, FORM_1))
        .willThrow(SignatureStateException.startedForAnotherEntity(FORM_1));

    assertThatThrownBy(() -> service.getSmartIdSignatureStatus(1L, person))
        .isInstanceOf(SignatureStateException.class);
    then(formService).should(never()).sign(any(), any(), any());
  }

  @Test
  void aMobileIdSessionStartedForAnotherEntityCannotFinishThisForm() {
    var mandateSession = mobileIdSession(MANDATE_1);
    given(sessionStore.get(MobileIdSignatureSession.class)).willReturn(Optional.of(mandateSession));
    given(formService.summary(1L, person)).willReturn(unsignedSummary());
    given(signatureService.getSignedFile(mandateSession, FORM_1))
        .willThrow(SignatureStateException.startedForAnotherEntity(FORM_1));

    assertThatThrownBy(() -> service.getMobileIdSignatureStatus(1L, person))
        .isInstanceOf(SignatureStateException.class);
    then(formService).should(never()).sign(any(), any(), any());
  }

  @Test
  void aRepeatedPollOfASignedFormReportsTheSignatureWithoutSigningOrEmailingAgain() {
    given(sessionStore.get(SmartIdSignatureSession.class))
        .willReturn(Optional.of(smartIdSession()));
    given(formService.summary(1L, person)).willReturn(signedSummary());

    MobileSignatureStatusResponse status = service.getSmartIdSignatureStatus(1L, person);

    assertThat(status.getStatusCode()).isEqualTo(SIGNATURE);
    then(signatureService).shouldHaveNoInteractions();
    then(formService).should(never()).sign(any(), any(), any());
  }

  @Test
  void doesNotStartSigningAFormThatIsAlreadySigned() {
    given(formService.get(1L, person)).willReturn(signedForm(1L, user.getId(), COMPENSA));

    assertThatThrownBy(() -> service.startSmartIdSignature(1L, person))
        .isInstanceOf(SignatureStateException.class);
    then(signatureService).shouldHaveNoInteractions();
  }

  @Test
  void needsASmartIdSessionToReportItsStatus() {
    given(sessionStore.get(SmartIdSignatureSession.class)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getSmartIdSignatureStatus(1L, person))
        .isInstanceOf(IdSessionException.class);
  }

  @Test
  void startsMobileIdSigningWithThePhoneNumberThePersonLoggedInWith() {
    var form = savedForm(1L, user.getId(), COMPENSA);
    given(formService.get(1L, person)).willReturn(form);
    var session = mobileIdSession();
    given(
            signatureService.startMobileIdSign(
                eq(FORM_1),
                argThat(isTheFilledForm(form)),
                eq(user.getPersonalCode()),
                eq("+37255555555")))
        .willReturn(session);

    MobileSignatureResponse response = service.startMobileIdSignature(1L, person);

    assertThat(response.getChallengeCode()).isEqualTo("5678");
    then(sessionStore).should().save(session);
  }

  @Test
  void finishesMobileIdSigningOfTheFormTheSessionWasStartedFor() {
    var session = mobileIdSession();
    given(sessionStore.get(MobileIdSignatureSession.class)).willReturn(Optional.of(session));
    given(formService.summary(1L, person)).willReturn(unsignedSummary());
    given(signatureService.getSignedFile(session, FORM_1)).willReturn(SIGNED_CONTAINER);

    MobileSignatureStatusResponse status = service.getMobileIdSignatureStatus(1L, person);

    assertThat(status).isEqualTo(new MobileSignatureStatusResponse(SIGNATURE, "5678"));
    then(formService).should().sign(1L, SIGNED_CONTAINER, Locale.ENGLISH);
  }

  @Test
  void startsIdCardSigningBoundToTheForm() {
    var form = savedForm(1L, user.getId(), COMPENSA);
    given(formService.get(1L, person)).willReturn(form);
    var session = idCardSession();
    given(
            signatureService.startIdCardSign(
                eq(FORM_1),
                argThat(isTheFilledForm(form)),
                eq("certificate"),
                eq(List.of("SHA-256")),
                eq(user.getPersonalCode())))
        .willReturn(session);

    IdCardSignatureResponse response =
        service.startIdCardSignature(
            1L, person, new StartIdCardSignCommand("certificate", List.of("SHA-256")));

    assertThat(response).isEqualTo(new IdCardSignatureResponse("hash", "SHA-256"));
    then(sessionStore).should().save(session);
  }

  @Test
  void finishesIdCardSigningWithTheSignatureFromTheCard() {
    var session = idCardSession();
    given(sessionStore.get(IdCardSignatureSession.class)).willReturn(Optional.of(session));
    given(formService.summary(1L, person)).willReturn(unsignedSummary());
    given(signatureService.getSignedFile(session, FORM_1, "signature"))
        .willReturn(SIGNED_CONTAINER);

    IdCardSignatureStatusResponse status =
        service.persistIdCardSignature(1L, new FinishIdCardSignCommand("signature"), person);

    assertThat(status).isEqualTo(new IdCardSignatureStatusResponse(SIGNATURE));
    then(formService).should().sign(1L, SIGNED_CONTAINER, Locale.ENGLISH);
  }

  @Test
  void doesNotTakeASecondIdCardSignatureForASignedForm() {
    given(sessionStore.get(IdCardSignatureSession.class)).willReturn(Optional.of(idCardSession()));
    given(formService.summary(1L, person)).willReturn(signedSummary());

    assertThatThrownBy(
            () ->
                service.persistIdCardSignature(
                    1L, new FinishIdCardSignCommand("signature"), person))
        .isInstanceOf(SignatureStateException.class);
    then(signatureService).shouldHaveNoInteractions();
  }

  @Test
  void reportsTheIdCardSignatureOnlyOnceTheFormIsSigned() {
    given(formService.summary(1L, person))
        .willReturn(unsignedSummary())
        .willReturn(signedSummary());

    assertThatThrownBy(() -> service.getIdCardSignatureStatus(1L, person))
        .isInstanceOf(SignatureStateException.class);
    assertThat(service.getIdCardSignatureStatus(1L, person))
        .isEqualTo(new IdCardSignatureStatusResponse(SIGNATURE));
  }

  private InsuranceTransferFormSummary unsignedSummary() {
    return savedForm(1L, user.getId(), COMPENSA).summary();
  }

  private InsuranceTransferFormSummary signedSummary() {
    return signedForm(1L, user.getId(), COMPENSA).summary();
  }

  private static ArgumentMatcher<List<SignatureFile>> isTheFilledForm(InsuranceTransferForm form) {
    return files ->
        files.size() == 1
            && files.getFirst().getName().equals("Tuleva_Compensa_avaldus.pdf")
            && files.getFirst().getMimeType().equals("application/pdf")
            && Arrays.equals(files.getFirst().getContent(), form.getFilledPdf());
  }

  private SmartIdSignatureSession smartIdSession() {
    return smartIdSession(FORM_1);
  }

  private SmartIdSignatureSession smartIdSession(SignableEntity startedFor) {
    var session = new SmartIdSignatureSession(startedFor, user.getPersonalCode(), List.of());
    session.setVerificationCode("1234");
    return session;
  }

  private static MobileIdSignatureSession mobileIdSession() {
    return mobileIdSession(FORM_1);
  }

  private static MobileIdSignatureSession mobileIdSession(SignableEntity startedFor) {
    return MobileIdSignatureSession.builder()
        .signableEntity(startedFor)
        .sessionId("session")
        .verificationCode("5678")
        .build();
  }

  private static IdCardSignatureSession idCardSession() {
    return IdCardSignatureSession.builder()
        .signableEntity(FORM_1)
        .hashToSign("hash")
        .hashFunction("SHA-256")
        .build();
  }
}
