package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.mandate.MandateFixture.*;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.INITIALIZED;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.SIGNED;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.WITHDRAWALS;
import static ee.tuleva.onboarding.signature.response.SignatureStatus.OUTSTANDING_TRANSACTION;
import static ee.tuleva.onboarding.signature.response.SignatureStatus.SIGNATURE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.error.response.ErrorResponse;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateFileService;
import ee.tuleva.onboarding.mandate.batch.poller.MandateBatchProcessingPoller;
import ee.tuleva.onboarding.mandate.event.AfterMandateBatchSignedEvent;
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import ee.tuleva.onboarding.mandate.exception.MandateProcessingException;
import ee.tuleva.onboarding.mandate.generic.GenericMandateService;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.signature.SignatureFile;
import ee.tuleva.onboarding.signature.SignatureService;
import ee.tuleva.onboarding.signature.idcard.IdCardSignatureSession;
import ee.tuleva.onboarding.signature.mobileid.MobileIdSignatureSession;
import ee.tuleva.onboarding.signature.response.SignatureStatus;
import ee.tuleva.onboarding.signature.smartid.SmartIdSignatureSession;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import ee.tuleva.onboarding.withdrawals.WithdrawalEligibilityDto;
import ee.tuleva.onboarding.withdrawals.WithdrawalEligibilityService;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
public class MandateBatchServiceTest {

  @Mock private MandateBatchRepository mandateBatchRepository;

  @Mock private MandateFileService mandateFileService;
  @Mock private GenericMandateService genericMandateService;
  @Mock private WithdrawalEligibilityService withdrawalEligibilityService;
  @Mock private UserService userService;
  @Mock private MandateProcessorService mandateProcessor;
  @Mock private MandateBatchProcessingPoller mandateBatchProcessingPoller;
  @Mock private EpisService episService;
  @Mock private ApplicationEventPublisher applicationEventPublisher;
  @Mock private OperationsNotificationService notificationService;

  @Mock private SignatureService signService;

  @InjectMocks private MandateBatchService mandateBatchService;

  @Test
  @DisplayName("return MandateBatch by id and user when all mandates belong to the user")
  void returnMandateBatch() {
    var user = sampleUser().build();
    var mandate1 = sampleFundPensionOpeningMandate();
    var mandate2 = samplePartialWithdrawalMandate();

    var mandateBatch =
        MandateBatchFixture.aMandateBatch().mandates(List.of(mandate1, mandate2)).build();

    when(mandateBatchRepository.findById(any())).thenReturn(Optional.of(mandateBatch));

    Optional<MandateBatch> result = mandateBatchService.getByIdAndUser(1L, user);

    assertThat(result.isPresent()).isTrue();
  }

  @Test
  @DisplayName("return empty when all mandates do not match the user")
  void mandatesDontMatch() {
    var user = sampleUser().build();
    var differentUser = sampleUser().id(2L).build();

    var mandate1 = sampleFundPensionOpeningMandate();
    var mandate2 = samplePartialWithdrawalMandate();
    mandate2.setUser(differentUser);

    var mandateBatch =
        MandateBatchFixture.aMandateBatch().mandates(List.of(mandate1, mandate2)).build();

    when(mandateBatchRepository.findById(any())).thenReturn(Optional.of(mandateBatch));

    Optional<MandateBatch> result = mandateBatchService.getByIdAndUser(1L, user);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("return mandate batch content files for user")
  void getMandateBatchContentFiles() {
    var mandate1 = sampleFundPensionOpeningMandate();
    var mandate2 = samplePartialWithdrawalMandate();
    var user = mandate1.getUser();

    var mandateBatch =
        MandateBatchFixture.aMandateBatch().mandates(List.of(mandate1, mandate2)).build();

    when(mandateBatchRepository.findById(1L)).thenReturn(Optional.of(mandateBatch));

    when(mandateFileService.getMandateFiles(mandate1))
        .thenReturn(List.of(new SignatureFile("file.html", "text/html", new byte[0])));
    when(mandateFileService.getMandateFiles(mandate2))
        .thenReturn(List.of(new SignatureFile("file2.html", "text/html", new byte[0])));

    List<SignatureFile> result = mandateBatchService.getMandateBatchContentFiles(1L, user);

    assertThat(result.size()).isEqualTo(2);
  }

  @Test
  @DisplayName("throw exception when MandateBatch not found for user")
  void notFoundMandateBatch() {
    var user = sampleUser().build();

    when(mandateBatchRepository.findById(1L)).thenReturn(Optional.empty());

    assertThrows(
        NoSuchElementException.class,
        () -> mandateBatchService.getMandateBatchContentFiles(1L, user));
    verify(mandateBatchRepository, times(1)).findById(1L);
  }

  @Test
  @DisplayName("throws when creating empty MandateBatch")
  void createEmptyMandateBatch() {
    var authenticatedPerson =
        AuthenticatedPersonFixture.authenticatedPersonFromUser(sampleUser().build()).build();

    var aMandateBatch = MandateBatch.builder().mandates(List.of()).status(INITIALIZED).build();
    var aMandateBatchDto = MandateBatchDto.from(aMandateBatch);

    assertThrows(
        IllegalArgumentException.class,
        () -> mandateBatchService.createMandateBatch(authenticatedPerson, aMandateBatchDto));
  }

  @Test
  @DisplayName("create MandateBatch")
  void createMandateBatch() {
    var authenticatedPerson =
        AuthenticatedPersonFixture.authenticatedPersonFromUser(sampleUser().build()).build();
    var aFundPensionOpeningMandate = sampleFundPensionOpeningMandate();

    var aMandateBatch =
        MandateBatch.builder()
            .mandates(List.of(aFundPensionOpeningMandate, aFundPensionOpeningMandate))
            .status(INITIALIZED)
            .build();
    var aMandateBatchDto = MandateBatchDto.from(aMandateBatch);

    var aWithdrawalEligibility =
        WithdrawalEligibilityDto.builder()
            .hasReachedEarlyRetirementAge(true)
            .canWithdrawThirdPillarWithReducedTax(true)
            .age(65)
            .recommendedDurationYears(20)
            .arrestsOrBankruptciesPresent(false)
            .build();

    when(withdrawalEligibilityService.getWithdrawalEligibility(authenticatedPerson))
        .thenReturn(aWithdrawalEligibility);
    when(genericMandateService.createGenericMandate(any(), any(), any()))
        .thenReturn(aFundPensionOpeningMandate);
    when(mandateBatchRepository.save(
            argThat(mandateBatch -> mandateBatch.getStatus().equals(INITIALIZED))))
        .thenReturn(aMandateBatch);

    MandateBatch result =
        mandateBatchService.createMandateBatch(authenticatedPerson, aMandateBatchDto);

    assertThat(result.getMandates().size()).isEqualTo(2);
    assertThat(result.getStatus()).isEqualTo(INITIALIZED);

    verify(notificationService, times(1))
        .sendMessage(
            argThat(
                message -> {
                  var age = PersonalCode.getAge(authenticatedPerson.getPersonalCode());
                  return message.contains("age=" + age)
                      && message.contains("SECOND")
                      && message.contains("FUND_PENSION_OPENING");
                }),
            eq(WITHDRAWALS));
  }

  @Test
  @DisplayName("create third pillar MandateBatch at 55+ with special case")
  void createMandateBatchThirdPillar55() {
    var authenticatedPerson =
        AuthenticatedPersonFixture.authenticatedPersonFromUser(sampleUser().build()).build();
    var aFundPensionOpeningMandate =
        sampleFundPensionOpeningMandate(aThirdPillarFundPensionOpeningMandateDetails);

    var aMandateBatch =
        MandateBatch.builder()
            .mandates(List.of(aFundPensionOpeningMandate, aFundPensionOpeningMandate))
            .status(INITIALIZED)
            .build();
    var aMandateBatchDto = MandateBatchDto.from(aMandateBatch);

    var aWithdrawalEligibility =
        WithdrawalEligibilityDto.builder()
            .hasReachedEarlyRetirementAge(false)
            .canWithdrawThirdPillarWithReducedTax(true)
            .age(56)
            .recommendedDurationYears(20)
            .arrestsOrBankruptciesPresent(false)
            .build();

    when(withdrawalEligibilityService.getWithdrawalEligibility(authenticatedPerson))
        .thenReturn(aWithdrawalEligibility);
    when(genericMandateService.createGenericMandate(any(), any(), any()))
        .thenReturn(aFundPensionOpeningMandate);
    when(mandateBatchRepository.save(
            argThat(mandateBatch -> mandateBatch.getStatus().equals(INITIALIZED))))
        .thenReturn(aMandateBatch);

    MandateBatch result =
        mandateBatchService.createMandateBatch(authenticatedPerson, aMandateBatchDto);

    assertThat(result.getMandates().size()).isEqualTo(2);
    assertThat(result.getStatus()).isEqualTo(INITIALIZED);

    verify(notificationService, times(1))
        .sendMessage(
            argThat(
                message -> {
                  var age = PersonalCode.getAge(authenticatedPerson.getPersonalCode());
                  return message.contains("age=" + age)
                      && message.contains("THIRD")
                      && message.contains("FUND_PENSION_OPENING");
                }),
            eq(WITHDRAWALS));
  }

  @Test
  @DisplayName("throws when creating MandateBatch before early retirement age")
  void createMandateBatchBeforeRetirementAge() {
    var authenticatedPerson =
        AuthenticatedPersonFixture.authenticatedPersonFromUser(sampleUser().build()).build();
    var aFundPensionOpeningMandate =
        sampleFundPensionOpeningMandate(aThirdPillarFundPensionOpeningMandateDetails);

    var aMandateBatch =
        MandateBatch.builder()
            .mandates(List.of(aFundPensionOpeningMandate))
            .status(INITIALIZED)
            .build();
    var aMandateBatchDto = MandateBatchDto.from(aMandateBatch);

    var aWithdrawalEligibility =
        WithdrawalEligibilityDto.builder()
            .hasReachedEarlyRetirementAge(false)
            .canWithdrawThirdPillarWithReducedTax(false)
            .age(35)
            .recommendedDurationYears(50)
            .arrestsOrBankruptciesPresent(false)
            .build();

    when(withdrawalEligibilityService.getWithdrawalEligibility(authenticatedPerson))
        .thenReturn(aWithdrawalEligibility);

    assertThrows(
        IllegalArgumentException.class,
        () -> mandateBatchService.createMandateBatch(authenticatedPerson, aMandateBatchDto));
  }

  @Test
  @DisplayName(
      "throws when creating second pillar MandateBatch before early retirement age with special case")
  void createSecondPillarMandateBatchBeforeRetirementAgeWithSpecialCase() {
    var authenticatedPerson =
        AuthenticatedPersonFixture.authenticatedPersonFromUser(sampleUser().build()).build();
    var aFundPensionOpeningMandate = sampleFundPensionOpeningMandate();

    var aMandateBatch =
        MandateBatch.builder()
            .mandates(List.of(aFundPensionOpeningMandate, aFundPensionOpeningMandate))
            .status(INITIALIZED)
            .build();
    var aMandateBatchDto = MandateBatchDto.from(aMandateBatch);

    var aWithdrawalEligibility =
        WithdrawalEligibilityDto.builder()
            .hasReachedEarlyRetirementAge(false)
            .canWithdrawThirdPillarWithReducedTax(true)
            .age(56)
            .recommendedDurationYears(50)
            .arrestsOrBankruptciesPresent(false)
            .build();

    when(withdrawalEligibilityService.getWithdrawalEligibility(authenticatedPerson))
        .thenReturn(aWithdrawalEligibility);

    assertThrows(
        IllegalArgumentException.class,
        () -> mandateBatchService.createMandateBatch(authenticatedPerson, aMandateBatchDto));
  }

  @Test
  @DisplayName(
      "throws when creating third pillar fund pension MandateBatch before early retirement age")
  void throwsThirdPillarFundPensionBeforeRetirementAge() {
    var authenticatedPerson =
        AuthenticatedPersonFixture.authenticatedPersonFromUser(sampleUser().build()).build();
    var aFundPensionOpeningMandate =
        sampleFundPensionOpeningMandate(aThirdPillarFundPensionOpeningMandateDetails);

    var aMandateBatch =
        MandateBatch.builder()
            .mandates(List.of(aFundPensionOpeningMandate))
            .status(INITIALIZED)
            .build();
    var aMandateBatchDto = MandateBatchDto.from(aMandateBatch);

    var aWithdrawalEligibility =
        WithdrawalEligibilityDto.builder()
            .hasReachedEarlyRetirementAge(false)
            .canWithdrawThirdPillarWithReducedTax(false)
            .age(25)
            .recommendedDurationYears(50)
            .arrestsOrBankruptciesPresent(false)
            .build();

    when(withdrawalEligibilityService.getWithdrawalEligibility(authenticatedPerson))
        .thenReturn(aWithdrawalEligibility);

    assertThrows(
        IllegalArgumentException.class,
        () -> mandateBatchService.createMandateBatch(authenticatedPerson, aMandateBatchDto));
  }

  @Test
  @DisplayName("allows single third pillar partial withdrawal application before retirement age")
  void thirdPillarPartialWithdrawalBeforeRetirementAge() {
    var authenticatedPerson =
        AuthenticatedPersonFixture.authenticatedPersonFromUser(sampleUser().build()).build();
    var aThirdPillarPartialWithdrawalMandate =
        samplePartialWithdrawalMandate(aThirdPillarPartialWithdrawalMandateDetails);

    var aMandateBatch =
        MandateBatch.builder()
            .mandates(List.of(aThirdPillarPartialWithdrawalMandate))
            .status(INITIALIZED)
            .build();
    var aMandateBatchDto = MandateBatchDto.from(aMandateBatch);

    var aWithdrawalEligibility =
        WithdrawalEligibilityDto.builder()
            .hasReachedEarlyRetirementAge(false)
            .canWithdrawThirdPillarWithReducedTax(false)
            .age(35)
            .recommendedDurationYears(50)
            .arrestsOrBankruptciesPresent(false)
            .build();

    when(withdrawalEligibilityService.getWithdrawalEligibility(authenticatedPerson))
        .thenReturn(aWithdrawalEligibility);

    when(genericMandateService.createGenericMandate(any(), any(), any()))
        .thenReturn(aThirdPillarPartialWithdrawalMandate);
    when(mandateBatchRepository.save(
            argThat(mandateBatch -> mandateBatch.getStatus().equals(INITIALIZED))))
        .thenReturn(aMandateBatch);

    MandateBatch result =
        mandateBatchService.createMandateBatch(authenticatedPerson, aMandateBatchDto);

    assertThat(result.getMandates().size()).isEqualTo(1);
    assertThat(result.getStatus()).isEqualTo(INITIALIZED);
    verify(notificationService, times(1))
        .sendMessage(
            argThat(
                message -> {
                  var age = PersonalCode.getAge(authenticatedPerson.getPersonalCode());
                  return message.contains("age=" + age)
                      && message.contains("THIRD")
                      && message.contains("PARTIAL_WITHDRAWAL");
                }),
            eq(WITHDRAWALS));
  }

  @Test
  @DisplayName("slack message send failure does not block creation")
  void slackMessageThrows() {
    var authenticatedPerson =
        AuthenticatedPersonFixture.authenticatedPersonFromUser(sampleUser().build()).build();
    var aFundPensionOpeningMandate = sampleFundPensionOpeningMandate();

    var aMandateBatch =
        MandateBatch.builder()
            .mandates(List.of(aFundPensionOpeningMandate, aFundPensionOpeningMandate))
            .status(INITIALIZED)
            .build();
    var aMandateBatchDto = MandateBatchDto.from(aMandateBatch);

    var aWithdrawalEligibility =
        WithdrawalEligibilityDto.builder()
            .hasReachedEarlyRetirementAge(true)
            .canWithdrawThirdPillarWithReducedTax(true)
            .age(65)
            .recommendedDurationYears(20)
            .arrestsOrBankruptciesPresent(false)
            .build();

    when(withdrawalEligibilityService.getWithdrawalEligibility(authenticatedPerson))
        .thenReturn(aWithdrawalEligibility);
    when(genericMandateService.createGenericMandate(any(), any(), any()))
        .thenReturn(aFundPensionOpeningMandate);
    when(mandateBatchRepository.save(
            argThat(mandateBatch -> mandateBatch.getStatus().equals(INITIALIZED))))
        .thenReturn(aMandateBatch);
    doThrow(new IllegalStateException()).when(notificationService).sendMessage(any(), any());

    MandateBatch result =
        mandateBatchService.createMandateBatch(authenticatedPerson, aMandateBatchDto);

    assertThat(result.getMandates().size()).isEqualTo(2);
    assertThat(result.getStatus()).isEqualTo(INITIALIZED);
  }

  User mockUser() {
    var user = sampleUser().build();
    when(userService.getById(user.getId())).thenReturn(Optional.of(user));

    return user;
  }

  @DisplayName("smart-id")
  @Nested
  class SmartIdTests {

    @Test
    @DisplayName(
        "finalizeSmartIdSignature handles signed mandate and all mandates processed successfully")
    void finalizeSmartIdSignatureHandlesSignedMandate_AllProcessed_Success() {
      Mandate mandate1 = sampleFundPensionOpeningMandate();
      Mandate mandate2 = samplePartialWithdrawalMandate();

      MandateBatch mandateBatch =
          MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));
      mandateBatch.setStatus(SIGNED);

      User user = mockUser();
      SmartIdSignatureSession session = mock(SmartIdSignatureSession.class);

      when(mandateBatchRepository.findById(mandateBatch.getId()))
          .thenReturn(Optional.of(mandateBatch));

      when(mandateProcessor.isFinished(mandate1)).thenReturn(true);
      when(mandateProcessor.isFinished(mandate2)).thenReturn(true);

      when(mandateProcessor.getErrors(mandate1)).thenReturn(new ErrorsResponse(List.of()));
      when(mandateProcessor.getErrors(mandate2)).thenReturn(new ErrorsResponse(List.of()));

      SignatureStatus status =
          mandateBatchService.finalizeMobileSignature(
              user.getId(), mandateBatch.getId(), session, Locale.ENGLISH);

      assertThat(SIGNATURE).isEqualTo(status);
      verify(episService, times(1)).clearCache(user);
      verify(applicationEventPublisher, times(2)).publishEvent(any(AfterMandateSignedEvent.class));
      verify(applicationEventPublisher, times(1))
          .publishEvent(any(AfterMandateBatchSignedEvent.class));
    }

    @Test
    @DisplayName("finalizeSmartIdSignature handles signed mandate with processing errors")
    void finalizeSmartIdSignatureHandlesSignedMandate_WithProcessingErrors() {
      Mandate mandate1 = sampleFundPensionOpeningMandate();
      Mandate mandate2 = samplePartialWithdrawalMandate();

      MandateBatch mandateBatch =
          MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));
      mandateBatch.setStatus(SIGNED);

      var user = mockUser();
      SmartIdSignatureSession session = mock(SmartIdSignatureSession.class);

      when(mandateBatchRepository.findById(mandateBatch.getId()))
          .thenReturn(Optional.of(mandateBatch));

      when(mandateProcessor.isFinished(mandate1)).thenReturn(true);
      when(mandateProcessor.isFinished(mandate2)).thenReturn(true);

      List<ErrorResponse> errors =
          List.of(
              new ErrorResponse("ERROR_CODE_1", "Error message 1", null, List.of()),
              new ErrorResponse("ERROR_CODE_2", "Error message 2", null, List.of()));
      when(mandateProcessor.getErrors(mandate1)).thenReturn(new ErrorsResponse(List.of()));
      when(mandateProcessor.getErrors(mandate2)).thenReturn(new ErrorsResponse(errors));

      MandateProcessingException exception =
          assertThrows(
              MandateProcessingException.class,
              () ->
                  mandateBatchService.finalizeMobileSignature(
                      user.getId(), mandateBatch.getId(), session, Locale.ENGLISH));

      assertThat(exception).isNotNull();
      assertThat(errors.size()).isEqualTo(2);

      verify(episService, times(1)).clearCache(user);
      verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName(
        "finalizeSmartIdSignature handles signed mandate but mandates are still processing")
    void finalizeSmartIdSignatureHandlesSignedMandate_MandatesStillProcessing() {
      Mandate mandate1 = sampleFundPensionOpeningMandate();
      Mandate mandate2 = samplePartialWithdrawalMandate();

      MandateBatch mandateBatch =
          MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));
      mandateBatch.setStatus(SIGNED);

      var user = mockUser();
      SmartIdSignatureSession session = mock(SmartIdSignatureSession.class);

      when(mandateBatchRepository.findById(mandateBatch.getId()))
          .thenReturn(Optional.of(mandateBatch));

      when(mandateProcessor.isFinished(mandate1)).thenReturn(true);
      when(mandateProcessor.isFinished(mandate2)).thenReturn(false);

      SignatureStatus status =
          mandateBatchService.finalizeMobileSignature(
              user.getId(), mandateBatch.getId(), session, Locale.ENGLISH);

      assertThat(OUTSTANDING_TRANSACTION).isEqualTo(status);
      verify(episService, never()).clearCache(any());
      verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("finalizeSmartIdSignature handles unsigned mandate with signed file")
    void finalizeSmartIdSignatureHandlesUnsignedMandate_WithSignedFile() {
      Mandate mandate1 = sampleFundPensionOpeningMandate();
      Mandate mandate2 = samplePartialWithdrawalMandate();

      byte[] signedFile = "signedFileBytes".getBytes();

      MandateBatch mandateBatch =
          MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));
      mandateBatch.setStatus(INITIALIZED);

      var user = mockUser();
      SmartIdSignatureSession session = mock(SmartIdSignatureSession.class);

      when(mandateBatchRepository.findById(mandateBatch.getId()))
          .thenReturn(Optional.of(mandateBatch));
      when(signService.getSignedFile(session)).thenReturn(signedFile);

      ArgumentCaptor<MandateBatch> mandateBatchCaptor = ArgumentCaptor.forClass(MandateBatch.class);

      SignatureStatus status =
          mandateBatchService.finalizeMobileSignature(
              user.getId(), mandateBatch.getId(), session, Locale.ENGLISH);

      assertThat(OUTSTANDING_TRANSACTION).isEqualTo(status);
      verify(mandateBatchRepository, times(1)).save(mandateBatchCaptor.capture());
      MandateBatch savedBatch = mandateBatchCaptor.getValue();
      assertThat(signedFile).isEqualTo(savedBatch.getFile());
      assertThat(SIGNED).isEqualTo(savedBatch.getStatus());
      verify(mandateProcessor, times(1)).start(user, mandate1);
      verify(mandateProcessor, times(1)).start(user, mandate2);
      verify(mandateBatchProcessingPoller, times(1))
          .startPollingForBatchProcessingFinished(mandateBatch, Locale.ENGLISH);
      verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("finalizeSmartIdSignature handles unsigned mandate without signed file")
    void finalizeSmartIdSignatureHandlesUnsignedMandate_WithoutSignedFile() {
      Mandate mandate1 = sampleFundPensionOpeningMandate();
      Mandate mandate2 = samplePartialWithdrawalMandate();

      MandateBatch mandateBatch =
          MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));
      mandateBatch.setStatus(INITIALIZED);

      var user = mockUser();
      SmartIdSignatureSession session = mock(SmartIdSignatureSession.class);

      when(mandateBatchRepository.findById(mandateBatch.getId()))
          .thenReturn(Optional.of(mandateBatch));
      when(signService.getSignedFile(session)).thenReturn(null);

      SignatureStatus status =
          mandateBatchService.finalizeMobileSignature(
              user.getId(), mandateBatch.getId(), session, Locale.ENGLISH);

      assertThat(OUTSTANDING_TRANSACTION).isEqualTo(status);
      verify(signService, times(1)).getSignedFile(session);
      verify(mandateBatchRepository, never()).save(any());
      verify(mandateProcessor, never()).start(any(), any());
      verify(applicationEventPublisher, never()).publishEvent(any());
    }
  }

  @DisplayName("mobile id")
  @Nested
  class MobileIdTests {

    @Test
    @DisplayName(
        "finalizeMobileIdSignature handles signed mandate and all mandates processed successfully")
    void finalizeMobileIdSignatureHandlesSignedMandateAndAllMandatesProcessedSuccessfully() {
      Mandate mandate1 = sampleFundPensionOpeningMandate();
      Mandate mandate2 = samplePartialWithdrawalMandate();

      MandateBatch mandateBatch =
          MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));
      mandateBatch.setStatus(SIGNED);

      var user = mockUser();
      MobileIdSignatureSession session = mock(MobileIdSignatureSession.class);

      when(mandateBatchRepository.findById(mandateBatch.getId()))
          .thenReturn(Optional.of(mandateBatch));

      when(mandateProcessor.isFinished(mandate1)).thenReturn(true);
      when(mandateProcessor.isFinished(mandate2)).thenReturn(true);

      when(mandateProcessor.getErrors(mandate1)).thenReturn(new ErrorsResponse(List.of()));
      when(mandateProcessor.getErrors(mandate2)).thenReturn(new ErrorsResponse(List.of()));

      SignatureStatus status =
          mandateBatchService.finalizeMobileSignature(
              user.getId(), mandateBatch.getId(), session, Locale.ENGLISH);

      assertThat(SIGNATURE).isEqualTo(status);
      verify(episService, times(1)).clearCache(user);
      verify(applicationEventPublisher, times(2)).publishEvent(any(AfterMandateSignedEvent.class));
      verify(applicationEventPublisher, times(1))
          .publishEvent(any(AfterMandateBatchSignedEvent.class));
    }

    @Test
    @DisplayName("finalizeMobileIdSignature handles signed mandate with processing errors")
    void finalizeMobileIdSignatureHandlesSignedMandateWithProcessingErrors() {
      Mandate mandate1 = sampleFundPensionOpeningMandate();
      Mandate mandate2 = samplePartialWithdrawalMandate();

      MandateBatch mandateBatch =
          MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));
      mandateBatch.setStatus(SIGNED);

      var user = mockUser();
      MobileIdSignatureSession session = mock(MobileIdSignatureSession.class);

      when(mandateBatchRepository.findById(mandateBatch.getId()))
          .thenReturn(Optional.of(mandateBatch));

      when(mandateProcessor.isFinished(mandate1)).thenReturn(true);
      when(mandateProcessor.isFinished(mandate2)).thenReturn(true);

      List<ErrorResponse> errors =
          List.of(
              new ErrorResponse("ERROR_CODE_1", "Error message 1", null, List.of()),
              new ErrorResponse("ERROR_CODE_2", "Error message 2", null, List.of()));
      when(mandateProcessor.getErrors(mandate1)).thenReturn(new ErrorsResponse(List.of()));
      when(mandateProcessor.getErrors(mandate2)).thenReturn(new ErrorsResponse(errors));

      MandateProcessingException exception =
          assertThrows(
              MandateProcessingException.class,
              () ->
                  mandateBatchService.finalizeMobileSignature(
                      user.getId(), mandateBatch.getId(), session, Locale.ENGLISH));

      assertThat(exception).isNotNull();
      assertThat(errors.size()).isEqualTo(2);

      verify(episService, times(1)).clearCache(user);
      verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName(
        "finalizeMobileIdSignature handles signed mandate but mandates are still processing")
    void finalizeMobileIdSignatureHandlesSignedMandateButMandateIsNotProcessing() {
      Mandate mandate1 = sampleFundPensionOpeningMandate();
      Mandate mandate2 = samplePartialWithdrawalMandate();

      MandateBatch mandateBatch =
          MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));
      mandateBatch.setStatus(SIGNED);

      var user = mockUser();
      MobileIdSignatureSession session = mock(MobileIdSignatureSession.class);

      when(mandateBatchRepository.findById(mandateBatch.getId()))
          .thenReturn(Optional.of(mandateBatch));

      when(mandateProcessor.isFinished(mandate1)).thenReturn(true);
      when(mandateProcessor.isFinished(mandate2)).thenReturn(false);

      SignatureStatus status =
          mandateBatchService.finalizeMobileSignature(
              user.getId(), mandateBatch.getId(), session, Locale.ENGLISH);

      assertThat(OUTSTANDING_TRANSACTION).isEqualTo(status);
      verify(episService, never()).clearCache(any());
      verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("finalizeMobileIdSignature handles unsigned mandate with signed file")
    void finalizeMobileIdSignatureHandlesUnsignedMandateWithSignedFile() {
      Mandate mandate1 = sampleFundPensionOpeningMandate();
      Mandate mandate2 = samplePartialWithdrawalMandate();

      byte[] signedFile = "signedFileBytes".getBytes();

      MandateBatch mandateBatch =
          MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));
      mandateBatch.setStatus(INITIALIZED);

      var user = mockUser();
      MobileIdSignatureSession session = mock(MobileIdSignatureSession.class);

      when(mandateBatchRepository.findById(mandateBatch.getId()))
          .thenReturn(Optional.of(mandateBatch));
      when(signService.getSignedFile(session)).thenReturn(signedFile);

      ArgumentCaptor<MandateBatch> mandateBatchCaptor = ArgumentCaptor.forClass(MandateBatch.class);

      SignatureStatus status =
          mandateBatchService.finalizeMobileSignature(
              user.getId(), mandateBatch.getId(), session, Locale.ENGLISH);

      assertThat(OUTSTANDING_TRANSACTION).isEqualTo(status);
      verify(mandateBatchRepository, times(1)).save(mandateBatchCaptor.capture());
      MandateBatch savedBatch = mandateBatchCaptor.getValue();
      assertThat(signedFile).isEqualTo(savedBatch.getFile());
      assertThat(SIGNED).isEqualTo(savedBatch.getStatus());
      verify(mandateProcessor, times(1)).start(user, mandate1);
      verify(mandateProcessor, times(1)).start(user, mandate2);
      verify(mandateBatchProcessingPoller, times(1))
          .startPollingForBatchProcessingFinished(mandateBatch, Locale.ENGLISH);
      verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("finalizeMobileIdSignature handles unsigned mandate without signed file")
    void finalizeMobileIdSignatureHandlesUnsignedMandateWithoutSignedFile() {
      Mandate mandate1 = sampleFundPensionOpeningMandate();
      Mandate mandate2 = samplePartialWithdrawalMandate();

      MandateBatch mandateBatch =
          MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));
      mandateBatch.setStatus(INITIALIZED);

      var user = mockUser();
      MobileIdSignatureSession session = mock(MobileIdSignatureSession.class);

      when(mandateBatchRepository.findById(mandateBatch.getId()))
          .thenReturn(Optional.of(mandateBatch));
      when(signService.getSignedFile(session)).thenReturn(null);

      SignatureStatus status =
          mandateBatchService.finalizeMobileSignature(
              user.getId(), mandateBatch.getId(), session, Locale.ENGLISH);

      assertThat(OUTSTANDING_TRANSACTION).isEqualTo(status);
      verify(signService, times(1)).getSignedFile(session);
      verify(mandateBatchRepository, never()).save(any());
      verify(mandateProcessor, never()).start(any(), any());
      verify(applicationEventPublisher, never()).publishEvent(any());
    }
  }

  @DisplayName("id card")
  @Nested
  class IdCardTests {

    @Test
    @DisplayName(
        "persistIdCardSignedFileOrGetBatchProcessingStatus handles signed mandate and all mandates processed successfully")
    void finalizeIdCardSignatureHandlesSignedMandateAndAllMandatesProcessedSuccessfully() {
      Mandate mandate1 = sampleFundPensionOpeningMandate();
      Mandate mandate2 = samplePartialWithdrawalMandate();

      MandateBatch mandateBatch =
          MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));
      mandateBatch.setStatus(SIGNED);

      var user = mockUser();
      IdCardSignatureSession session = mock(IdCardSignatureSession.class);

      when(mandateBatchRepository.findById(mandateBatch.getId()))
          .thenReturn(Optional.of(mandateBatch));

      when(mandateProcessor.isFinished(mandate1)).thenReturn(true);
      when(mandateProcessor.isFinished(mandate2)).thenReturn(true);

      when(mandateProcessor.getErrors(mandate1)).thenReturn(new ErrorsResponse(List.of()));
      when(mandateProcessor.getErrors(mandate2)).thenReturn(new ErrorsResponse(List.of()));

      SignatureStatus status =
          mandateBatchService.persistIdCardSignedFileOrGetBatchProcessingStatus(
              user.getId(), mandateBatch.getId(), session, "hash", Locale.ENGLISH);

      assertThat(SIGNATURE).isEqualTo(status);
      verify(episService, times(1)).clearCache(user);
      verify(applicationEventPublisher, times(2)).publishEvent(any(AfterMandateSignedEvent.class));
      verify(applicationEventPublisher, times(1))
          .publishEvent(any(AfterMandateBatchSignedEvent.class));
    }

    @Test
    @DisplayName(
        "persistIdCardSignedFileOrGetBatchProcessingStatus handles signed mandate with processing errors")
    void finalizeIdCardSignatureHandlesSignedMandateWithProcessingErrors() {
      Mandate mandate1 = sampleFundPensionOpeningMandate();
      Mandate mandate2 = samplePartialWithdrawalMandate();

      MandateBatch mandateBatch =
          MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));
      mandateBatch.setStatus(SIGNED);

      var user = mockUser();
      IdCardSignatureSession session = mock(IdCardSignatureSession.class);

      when(mandateBatchRepository.findById(mandateBatch.getId()))
          .thenReturn(Optional.of(mandateBatch));

      when(mandateProcessor.isFinished(mandate1)).thenReturn(true);
      when(mandateProcessor.isFinished(mandate2)).thenReturn(true);

      List<ErrorResponse> errors =
          List.of(
              new ErrorResponse("ERROR_CODE_1", "Error message 1", null, List.of()),
              new ErrorResponse("ERROR_CODE_2", "Error message 2", null, List.of()));
      when(mandateProcessor.getErrors(mandate1)).thenReturn(new ErrorsResponse(List.of()));
      when(mandateProcessor.getErrors(mandate2)).thenReturn(new ErrorsResponse(errors));

      MandateProcessingException exception =
          assertThrows(
              MandateProcessingException.class,
              () ->
                  mandateBatchService.persistIdCardSignedFileOrGetBatchProcessingStatus(
                      user.getId(), mandateBatch.getId(), session, "hash", Locale.ENGLISH));

      assertThat(exception).isNotNull();
      assertThat(errors.size()).isEqualTo(2);

      verify(episService, times(1)).clearCache(user);
      verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName(
        "persistIdCardSignedFileOrGetBatchProcessingStatus handles signed mandate when mandates are still processing")
    void finalizeIdCardSignatureHandlesSignedMandateButMandateIsProcessing() {
      Mandate mandate1 = sampleFundPensionOpeningMandate();
      Mandate mandate2 = samplePartialWithdrawalMandate();

      MandateBatch mandateBatch =
          MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));
      mandateBatch.setStatus(SIGNED);

      var user = mockUser();
      IdCardSignatureSession session = mock(IdCardSignatureSession.class);

      when(mandateBatchRepository.findById(mandateBatch.getId()))
          .thenReturn(Optional.of(mandateBatch));

      when(mandateProcessor.isFinished(mandate1)).thenReturn(true);
      when(mandateProcessor.isFinished(mandate2)).thenReturn(false);

      SignatureStatus status =
          mandateBatchService.persistIdCardSignedFileOrGetBatchProcessingStatus(
              user.getId(), mandateBatch.getId(), session, "hash", Locale.ENGLISH);

      assertThat(OUTSTANDING_TRANSACTION).isEqualTo(status);
      verify(episService, never()).clearCache(any());
      verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName(
        "persistIdCardSignedFileOrGetBatchProcessingStatus handles signed file and starts processing mandates")
    void finalizeIdCardSignatureHandlesUnsignedMandateWithSignedFile() {
      Mandate mandate1 = sampleFundPensionOpeningMandate();
      Mandate mandate2 = samplePartialWithdrawalMandate();

      byte[] signedFile = "signedFileBytes".getBytes();

      MandateBatch mandateBatch =
          MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));
      mandateBatch.setStatus(INITIALIZED);

      var user = mockUser();
      IdCardSignatureSession session = mock(IdCardSignatureSession.class);

      when(mandateBatchRepository.findById(mandateBatch.getId()))
          .thenReturn(Optional.of(mandateBatch));
      when(signService.getSignedFile(eq(session), any())).thenReturn(signedFile);

      ArgumentCaptor<MandateBatch> mandateBatchCaptor = ArgumentCaptor.forClass(MandateBatch.class);

      SignatureStatus status =
          mandateBatchService.persistIdCardSignedFileOrGetBatchProcessingStatus(
              user.getId(), mandateBatch.getId(), session, "hash", Locale.ENGLISH);

      assertThat(OUTSTANDING_TRANSACTION).isEqualTo(status);
      verify(mandateBatchRepository, times(1)).save(mandateBatchCaptor.capture());
      MandateBatch savedBatch = mandateBatchCaptor.getValue();
      assertThat(signedFile).isEqualTo(savedBatch.getFile());
      assertThat(SIGNED).isEqualTo(savedBatch.getStatus());
      verify(mandateProcessor, times(1)).start(user, mandate1);
      verify(mandateProcessor, times(1)).start(user, mandate2);
      verify(mandateBatchProcessingPoller, times(1))
          .startPollingForBatchProcessingFinished(mandateBatch, Locale.ENGLISH);
      verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName(
        "persistIdCardSignedFileOrGetBatchProcessingStatus throws when signed file is missing")
    void finalizeIdCardSignatureHandlesUnsignedMandateWithoutSignedFile() {
      Mandate mandate1 = sampleFundPensionOpeningMandate();
      Mandate mandate2 = samplePartialWithdrawalMandate();

      MandateBatch mandateBatch =
          MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));
      mandateBatch.setStatus(INITIALIZED);

      var user = mockUser();
      IdCardSignatureSession session = mock(IdCardSignatureSession.class);

      when(mandateBatchRepository.findById(mandateBatch.getId()))
          .thenReturn(Optional.of(mandateBatch));
      when(signService.getSignedFile(eq(session), any())).thenReturn(null);

      assertThrows(
          IllegalStateException.class,
          () ->
              mandateBatchService.persistIdCardSignedFileOrGetBatchProcessingStatus(
                  user.getId(), mandateBatch.getId(), session, "hash", Locale.ENGLISH));

      verify(signService, times(1)).getSignedFile(eq(session), any());
      verify(mandateBatchRepository, never()).save(any());
      verify(mandateProcessor, never()).start(any(), any());
      verify(applicationEventPublisher, never()).publishEvent(any());
    }
  }
}
