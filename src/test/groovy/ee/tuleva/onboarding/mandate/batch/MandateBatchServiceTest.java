package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.mandate.MandateFixture.*;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.INITIALIZED;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.SIGNED;
import static ee.tuleva.onboarding.mandate.response.MandateSignatureStatus.OUTSTANDING_TRANSACTION;
import static ee.tuleva.onboarding.mandate.response.MandateSignatureStatus.SIGNATURE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.error.response.ErrorResponse;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateFileService;
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import ee.tuleva.onboarding.mandate.exception.MandateProcessingException;
import ee.tuleva.onboarding.mandate.generic.GenericMandateService;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import ee.tuleva.onboarding.mandate.response.MandateSignatureStatus;
import ee.tuleva.onboarding.mandate.signature.SignatureFile;
import ee.tuleva.onboarding.mandate.signature.SignatureService;
import ee.tuleva.onboarding.mandate.signature.idcard.IdCardSignatureSession;
import ee.tuleva.onboarding.mandate.signature.mobileid.MobileIdSignatureSession;
import ee.tuleva.onboarding.mandate.signature.smartid.SmartIdSignatureSession;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
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
  @Mock private UserService userService;
  @Mock private MandateProcessorService mandateProcessor;
  @Mock private EpisService episService;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

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

    when(genericMandateService.createGenericMandate(any(), any(), any()))
        .thenReturn(aFundPensionOpeningMandate);
    when(mandateBatchRepository.save(
            argThat(mandateBatch -> mandateBatch.getStatus().equals(INITIALIZED))))
        .thenReturn(aMandateBatch);

    MandateBatch result =
        mandateBatchService.createMandateBatch(authenticatedPerson, aMandateBatchDto);

    assertThat(result.getMandates().size()).isEqualTo(2);
    assertThat(result.getStatus()).isEqualTo(INITIALIZED);
  }

  User mockUser() {
    var user = sampleUser().build();
    when(userService.getById(user.getId())).thenReturn(user);

    return user;
  }

  @DisplayName("smart-id")
  @Nested
  class SmartIdTests {

    @Test
    @DisplayName("smart-id signing works")
    void smartIdSigningWorks() {
      var mandate1 = sampleFundPensionOpeningMandate();
      var mandate2 = samplePartialWithdrawalMandate();

      var mandateBatch = MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));

      var user = mockUser();
      var signatureSession =
          new SmartIdSignatureSession("sampleId", user.getPersonalCode(), List.of());

      when(mandateBatchRepository.findById(any())).thenReturn(Optional.of(mandateBatch));
      when(mandateFileService.getMandateFiles(mandate1))
          .thenReturn(List.of(new SignatureFile("file.html", "text/html", new byte[0])));
      when(mandateFileService.getMandateFiles(mandate2))
          .thenReturn(List.of(new SignatureFile("file2.html", "text/html", new byte[0])));

      when(signService.startSmartIdSign(any(), any())).thenReturn(signatureSession);

      var session = mandateBatchService.smartIdSign(mandateBatch.getId(), user.getId());
      assertThat(session).isEqualTo(signatureSession);
    }

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

      MandateSignatureStatus status =
          mandateBatchService.finalizeSmartIdSignature(
              user.getId(), mandateBatch.getId(), session, Locale.ENGLISH);

      assertThat(SIGNATURE).isEqualTo(status);
      verify(episService, times(1)).clearCache(user);
      verify(applicationEventPublisher, times(2)).publishEvent(any(AfterMandateSignedEvent.class));
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
                  mandateBatchService.finalizeSmartIdSignature(
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

      MandateSignatureStatus status =
          mandateBatchService.finalizeSmartIdSignature(
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

      MandateSignatureStatus status =
          mandateBatchService.finalizeSmartIdSignature(
              user.getId(), mandateBatch.getId(), session, Locale.ENGLISH);

      assertThat(OUTSTANDING_TRANSACTION).isEqualTo(status);
      verify(mandateBatchRepository, times(1)).save(mandateBatchCaptor.capture());
      MandateBatch savedBatch = mandateBatchCaptor.getValue();
      assertThat(signedFile).isEqualTo(savedBatch.getFile());
      assertThat(SIGNED).isEqualTo(savedBatch.getStatus());
      verify(mandateProcessor, times(1)).start(user, mandate1);
      verify(mandateProcessor, times(1)).start(user, mandate2);
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

      MandateSignatureStatus status =
          mandateBatchService.finalizeSmartIdSignature(
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
    @DisplayName("mobile id signing works")
    void mobileIdSigningWorks() {
      var mandate1 = sampleFundPensionOpeningMandate();
      var mandate2 = samplePartialWithdrawalMandate();

      var mandateBatch = MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));

      var user = sampleUser().build();
      var signatureSession = MobileIdSignatureSession.builder().build();

      when(mandateBatchRepository.findById(any())).thenReturn(Optional.of(mandateBatch));
      when(userService.getById(any())).thenReturn(user);
      when(mandateFileService.getMandateFiles(mandate1))
          .thenReturn(List.of(new SignatureFile("file.html", "text/html", new byte[0])));
      when(mandateFileService.getMandateFiles(mandate2))
          .thenReturn(List.of(new SignatureFile("file2.html", "text/html", new byte[0])));

      when(signService.startMobileIdSign(any(), any(), any())).thenReturn(signatureSession);

      var session =
          mandateBatchService.mobileIdSign(
              mandateBatch.getId(), user.getId(), user.getPhoneNumber());
      assertThat(session).isEqualTo(signatureSession);
    }

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

      MandateSignatureStatus status =
          mandateBatchService.finalizeMobileIdSignature(
              user.getId(), mandateBatch.getId(), session, Locale.ENGLISH);

      assertThat(SIGNATURE).isEqualTo(status);
      verify(episService, times(1)).clearCache(user);
      verify(applicationEventPublisher, times(2)).publishEvent(any(AfterMandateSignedEvent.class));
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
                  mandateBatchService.finalizeMobileIdSignature(
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

      MandateSignatureStatus status =
          mandateBatchService.finalizeMobileIdSignature(
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

      MandateSignatureStatus status =
          mandateBatchService.finalizeMobileIdSignature(
              user.getId(), mandateBatch.getId(), session, Locale.ENGLISH);

      assertThat(OUTSTANDING_TRANSACTION).isEqualTo(status);
      verify(mandateBatchRepository, times(1)).save(mandateBatchCaptor.capture());
      MandateBatch savedBatch = mandateBatchCaptor.getValue();
      assertThat(signedFile).isEqualTo(savedBatch.getFile());
      assertThat(SIGNED).isEqualTo(savedBatch.getStatus());
      verify(mandateProcessor, times(1)).start(user, mandate1);
      verify(mandateProcessor, times(1)).start(user, mandate2);
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

      MandateSignatureStatus status =
          mandateBatchService.finalizeMobileIdSignature(
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
    @DisplayName("id card signing works")
    void idCardSigningWorks() {
      var mandate1 = sampleFundPensionOpeningMandate();
      var mandate2 = samplePartialWithdrawalMandate();

      var mandateBatch = MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));

      var user = sampleUser().build();
      var signatureSession = IdCardSignatureSession.builder().build();

      when(mandateBatchRepository.findById(any())).thenReturn(Optional.of(mandateBatch));
      when(userService.getById(any())).thenReturn(user);
      when(mandateFileService.getMandateFiles(mandate1))
          .thenReturn(List.of(new SignatureFile("file.html", "text/html", new byte[0])));
      when(mandateFileService.getMandateFiles(mandate2))
          .thenReturn(List.of(new SignatureFile("file2.html", "text/html", new byte[0])));

      when(signService.startIdCardSign(any(), any())).thenReturn(signatureSession);

      var session =
          mandateBatchService.idCardSign(mandateBatch.getId(), user.getId(), user.getPhoneNumber());
      assertThat(session).isEqualTo(signatureSession);
    }

    @Test
    @DisplayName(
        "finalizeIdCardSignature handles signed mandate and all mandates processed successfully")
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

      MandateSignatureStatus status =
          mandateBatchService.finalizeIdCardSignature(
              user.getId(), mandateBatch.getId(), session, "hash", Locale.ENGLISH);

      assertThat(SIGNATURE).isEqualTo(status);
      verify(episService, times(1)).clearCache(user);
      verify(applicationEventPublisher, times(2)).publishEvent(any(AfterMandateSignedEvent.class));
    }

    @Test
    @DisplayName("finalizeIdCardSignature handles signed mandate with processing errors")
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
                  mandateBatchService.finalizeIdCardSignature(
                      user.getId(), mandateBatch.getId(), session, "hash", Locale.ENGLISH));

      assertThat(exception).isNotNull();
      assertThat(errors.size()).isEqualTo(2);

      verify(episService, times(1)).clearCache(user);
      verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("finalizeIdCardSignature handles signed mandate but mandates are still processing")
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

      MandateSignatureStatus status =
          mandateBatchService.finalizeIdCardSignature(
              user.getId(), mandateBatch.getId(), session, "hash", Locale.ENGLISH);

      assertThat(OUTSTANDING_TRANSACTION).isEqualTo(status);
      verify(episService, never()).clearCache(any());
      verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("finalizeIdCardSignature handles unsigned mandate with signed file")
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

      MandateSignatureStatus status =
          mandateBatchService.finalizeIdCardSignature(
              user.getId(), mandateBatch.getId(), session, "hash", Locale.ENGLISH);

      assertThat(OUTSTANDING_TRANSACTION).isEqualTo(status);
      verify(mandateBatchRepository, times(1)).save(mandateBatchCaptor.capture());
      MandateBatch savedBatch = mandateBatchCaptor.getValue();
      assertThat(signedFile).isEqualTo(savedBatch.getFile());
      assertThat(SIGNED).isEqualTo(savedBatch.getStatus());
      verify(mandateProcessor, times(1)).start(user, mandate1);
      verify(mandateProcessor, times(1)).start(user, mandate2);
      verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("finalizeIdCardSignature handles unsigned mandate without signed file")
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
              mandateBatchService.finalizeIdCardSignature(
                  user.getId(), mandateBatch.getId(), session, "hash", Locale.ENGLISH));

      verify(signService, times(1)).getSignedFile(eq(session), any());
      verify(mandateBatchRepository, never()).save(any());
      verify(mandateProcessor, never()).start(any(), any());
      verify(applicationEventPublisher, never()).publishEvent(any());
    }
  }
}
