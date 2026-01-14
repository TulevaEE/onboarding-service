package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.WITHDRAWALS;
import static ee.tuleva.onboarding.pillar.Pillar.SECOND;
import static ee.tuleva.onboarding.signature.response.SignatureStatus.OUTSTANDING_TRANSACTION;
import static ee.tuleva.onboarding.signature.response.SignatureStatus.SIGNATURE;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.error.response.ErrorResponse;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.MandateFileService;
import ee.tuleva.onboarding.mandate.batch.poller.MandateBatchProcessingPoller;
import ee.tuleva.onboarding.mandate.event.AfterMandateBatchSignedEvent;
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import ee.tuleva.onboarding.mandate.exception.MandateProcessingException;
import ee.tuleva.onboarding.mandate.generic.GenericMandateService;
import ee.tuleva.onboarding.mandate.generic.MandateDto;
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
import ee.tuleva.onboarding.withdrawals.WithdrawalEligibilityService;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MandateBatchService {
  private final MandateBatchRepository mandateBatchRepository;

  private final MandateFileService mandateFileService;
  private final WithdrawalEligibilityService withdrawalEligibilityService;
  private final GenericMandateService genericMandateService;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final UserService userService;
  private final SignatureService signService;
  private final MandateProcessorService mandateProcessor;
  private final EpisService episService;
  private final MandateBatchProcessingPoller mandateBatchProcessingPoller;
  private final OperationsNotificationService notificationService;

  public Optional<MandateBatch> getByIdAndUser(Long id, User user) {
    var batch =
        mandateBatchRepository
            .findById(id)
            .filter(
                mandateBatch ->
                    mandateBatch.getMandates().stream()
                        .allMatch(mandate -> mandate.getUser().equals(user)));

    if (batch.isEmpty()) {
      log.error("Invalid user {} for mandate batch {}", user.getId(), id);
    }

    return batch;
  }

  public MandateBatch createMandateBatch(
      AuthenticatedPerson authenticatedPerson, MandateBatchDto mandateBatchDto) {

    if (mandateBatchDto.getMandates().isEmpty()) {
      throw new IllegalArgumentException("Mandate batch must have at least one mandate");
    }

    if (mandateBatchDto.isWithdrawalBatch()) {
      var eligibility = withdrawalEligibilityService.getWithdrawalEligibility(authenticatedPerson);

      if (eligibility.canWithdrawThirdPillarWithReducedTax()
          && !eligibility.hasReachedEarlyRetirementAge()) {
        var withdrawalBatchPillars = mandateBatchDto.getWithdrawalBatchPillars();

        if (withdrawalBatchPillars.contains(SECOND)) {
          throw new IllegalArgumentException(
              "Can only create third pillar withdrawals before retirement age");
        }
      } else if (!mandateBatchDto.isBatchOnlyThirdPillarPartialWithdrawal()
          && !eligibility.hasReachedEarlyRetirementAge()) {
        throw new IllegalArgumentException(
            "Can only do partial withdrawal from III pillar before early retirement age");
      }
    }

    var mandateBatch = MandateBatch.builder().status(MandateBatchStatus.INITIALIZED).build();

    var savedMandateBatch = mandateBatchRepository.save(mandateBatch);
    var mandates =
        mandateBatchDto.getMandates().stream()
            .map(
                mandateDto ->
                    genericMandateService.createGenericMandate(
                        authenticatedPerson, mandateDto, mandateBatch))
            .collect(toList());

    try {
      int age = PersonalCode.getAge(authenticatedPerson.getPersonalCode());

      var pillars = mandateBatchDto.getWithdrawalBatchPillars();
      var withdrawalTypes =
          mandateBatchDto.getMandates().stream()
              .map(MandateDto::getMandateType)
              .collect(Collectors.toSet());

      notificationService.sendMessage(
          "Withdrawal mandate batch created: age=%s, pillars=%s, withdrawalTypes=%s, mandateBatchId=%s"
              .formatted(age, pillars, withdrawalTypes, mandateBatch.getId()),
          WITHDRAWALS);
    } catch (Exception e) {
      log.error("Failed to send mandate batch slack message with exception", e);
    }

    savedMandateBatch.setMandates(mandates);

    return mandateBatchRepository.save(mandateBatch);
  }

  public List<SignatureFile> getMandateBatchContentFiles(Long mandateBatchId, User user) {
    var mandateBatch = getByIdAndUser(mandateBatchId, user).orElseThrow();

    return mandateBatch.getMandates().stream()
        .map(mandateFileService::getMandateFiles)
        .flatMap(List::stream)
        .toList();
  }

  public SignatureStatus finalizeMobileSignature(
      Long userId, Long mandateBatchId, SmartIdSignatureSession session, Locale locale) {
    var signedFile = Optional.ofNullable(signService.getSignedFile(session));
    return checkIfFileSignedToStartProcessing(userId, mandateBatchId, signedFile, locale);
  }

  public SignatureStatus finalizeMobileSignature(
      Long userId, Long mandateBatchId, MobileIdSignatureSession session, Locale locale) {
    var signedFile = Optional.ofNullable(signService.getSignedFile(session));
    return checkIfFileSignedToStartProcessing(userId, mandateBatchId, signedFile, locale);
  }

  private SignatureStatus checkIfFileSignedToStartProcessing(
      Long userId, Long mandateBatchId, Optional<byte[]> signedFile, Locale locale) {
    User user = userService.getById(userId).orElseThrow();
    MandateBatch mandateBatch = getByIdAndUser(mandateBatchId, user).orElseThrow();

    if (!mandateBatch.isSigned()) {
      return persistSignedFileIfPresentAndStartProcessing(user, mandateBatch, signedFile, locale);
    }

    return getBatchProcessingStatusAndHandleIfProcessed(user, mandateBatch, locale);
  }

  private SignatureStatus persistFileSignedWithIdCard(
      User user,
      MandateBatch mandateBatch,
      IdCardSignatureSession session,
      String signedHashInHex,
      Locale locale) {
    byte[] signedFile = signService.getSignedFile(session, signedHashInHex);

    if (signedFile == null) { // TODO: use Optional
      throw new IllegalStateException("There is no signed file to persist");
    }

    return persistSignedFileIfPresentAndStartProcessing(
        user, mandateBatch, Optional.of(signedFile), locale);
  }

  public SignatureStatus persistIdCardSignedFileOrGetBatchProcessingStatus(
      Long userId,
      Long mandateBatchId,
      IdCardSignatureSession session,
      String signedHashInHex,
      Locale locale) {
    User user = userService.getById(userId).orElseThrow();
    MandateBatch mandateBatch = getByIdAndUser(mandateBatchId, user).orElseThrow();

    if (!mandateBatch.isSigned()) {
      return persistFileSignedWithIdCard(user, mandateBatch, session, signedHashInHex, locale);
    }

    return getBatchProcessingStatusAndHandleIfProcessed(user, mandateBatch, locale);
  }

  private SignatureStatus getBatchProcessingStatusAndHandleIfProcessed(
      User user, MandateBatch mandateBatch, Locale locale) {

    var allMandatesHaveFinishedProcessing =
        mandateBatch.getMandates().stream()
            .allMatch(mandate -> mandateProcessor.isFinished(mandate));

    if (allMandatesHaveFinishedProcessing) {
      onMandateProcessingFinished(user, mandateBatch, locale);
      return SIGNATURE;
    } else {
      return OUTSTANDING_TRANSACTION;
    }
  }

  private void onMandateProcessingFinished(User user, MandateBatch mandateBatch, Locale locale) {
    episService.clearCache(user);
    handleMandateProcessingErrors(mandateBatch);
    notifyAboutSignedMandate(user, mandateBatch, locale);
  }

  private void handleMandateProcessingErrors(MandateBatch mandateBatch) {
    var mandates = mandateBatch.getMandates();

    List<ErrorResponse> errorResponses =
        mandates.stream()
            .map(mandate -> mandateProcessor.getErrors(mandate).getErrors())
            .flatMap(List::stream)
            .toList();

    ErrorsResponse errorsResponse = new ErrorsResponse(errorResponses);

    if (errorsResponse.hasErrors()) {
      log.info("Mandate batch processing errors {}", errorsResponse);
      throw new MandateProcessingException(errorsResponse);
    }
  }

  private SignatureStatus persistSignedFileIfPresentAndStartProcessing(
      User user, MandateBatch mandateBatch, Optional<byte[]> signedFile, Locale locale) {
    signedFile.ifPresent(
        it -> {
          persistSignedFile(mandateBatch, it);
          startProcessingBatch(user, mandateBatch);
          mandateBatchProcessingPoller.startPollingForBatchProcessingFinished(mandateBatch, locale);
        });

    return OUTSTANDING_TRANSACTION;
  }

  private void startProcessingBatch(User user, MandateBatch mandateBatch) {
    log.info(
        "Start mandate batch processing user id {} and mandate batch id {}",
        user.getId(),
        mandateBatch.getId());

    mandateBatch.getMandates().forEach(mandate -> mandateProcessor.start(user, mandate));
  }

  private void notifyAboutSignedMandate(User user, MandateBatch mandateBatch, Locale locale) {
    mandateBatch
        .getMandates()
        .forEach(
            mandate ->
                applicationEventPublisher.publishEvent(
                    new AfterMandateSignedEvent(this, user, mandate, locale)));

    applicationEventPublisher.publishEvent(
        new AfterMandateBatchSignedEvent(this, user, mandateBatch, locale));
  }

  private void persistSignedFile(MandateBatch mandateBatch, byte[] signedFile) {
    mandateBatch.setFile(signedFile);
    mandateBatch.setStatus(MandateBatchStatus.SIGNED);
    mandateBatchRepository.save(mandateBatch);
  }
}
