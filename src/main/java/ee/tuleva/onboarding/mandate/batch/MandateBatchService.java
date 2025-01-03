package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.mandate.MandateType.FUND_PENSION_OPENING;
import static ee.tuleva.onboarding.mandate.MandateType.PARTIAL_WITHDRAWAL;
import static ee.tuleva.onboarding.mandate.response.MandateSignatureStatus.*;
import static ee.tuleva.onboarding.mandate.response.MandateSignatureStatus.SIGNATURE;
import static ee.tuleva.onboarding.pillar.Pillar.SECOND;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.mandate.details.FundPensionOpeningMandateDetails;
import ee.tuleva.onboarding.epis.mandate.details.PartialWithdrawalMandateDetails;
import ee.tuleva.onboarding.error.response.ErrorResponse;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.MandateFileService;
import ee.tuleva.onboarding.mandate.event.AfterMandateBatchSignedEvent;
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
import ee.tuleva.onboarding.pillar.Pillar;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import ee.tuleva.onboarding.withdrawals.WithdrawalEligibilityService;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

    if (isWithdrawalBatch(mandateBatchDto)) {
      var eligibility = withdrawalEligibilityService.getWithdrawalEligibility(authenticatedPerson);

      if (eligibility.canWithdrawThirdPillarWithReducedTax()
          && !eligibility.hasReachedEarlyRetirementAge()) {
        var withdrawalBatchPillars = getWithdrawalBatchPillars(mandateBatchDto);

        if (withdrawalBatchPillars.contains(SECOND)) {
          throw new IllegalArgumentException(
              "Can only create third pillar withdrawals before retirement age");
        }
      } else if (!eligibility.hasReachedEarlyRetirementAge()) {
        throw new IllegalArgumentException(
            "Cannot create withdrawal mandates before early retirement age");
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

  public SmartIdSignatureSession smartIdSign(Long mandateBatchId, Long userId) {
    User user = userService.getById(userId);
    List<SignatureFile> files = getMandateBatchContentFiles(mandateBatchId, user);
    return signService.startSmartIdSign(files, user.getPersonalCode());
  }

  public MandateSignatureStatus finalizeSmartIdSignature(
      Long userId, Long mandateBatchId, SmartIdSignatureSession session, Locale locale) {
    User user = userService.getById(userId);
    MandateBatch mandateBatch = getByIdAndUser(mandateBatchId, user).orElseThrow();

    if (!mandateBatch.isSigned()) {
      return handleUnsignedMandateSmartId(user, mandateBatch, session);
    }

    return getMandateProcessingStatus(user, mandateBatch, locale);
  }

  public IdCardSignatureSession idCardSign(
      Long mandateBatchId, Long userId, String signingCertificate) {
    User user = userService.getById(userId);

    List<SignatureFile> files = getMandateBatchContentFiles(mandateBatchId, user);
    return signService.startIdCardSign(files, signingCertificate);
  }

  private boolean isWithdrawalBatch(MandateBatchDto mandateBatchDto) {
    return mandateBatchDto.getMandates().stream()
        .anyMatch(mandateDto -> mandateDto.getMandateType().isWithdrawalType());
  }

  private Set<Pillar> getWithdrawalBatchPillars(MandateBatchDto mandateBatchDto) {
    var fundPensionOpeningMandatePillars =
        mandateBatchDto.getMandates().stream()
            .filter(mandate -> mandate.getMandateType() == FUND_PENSION_OPENING)
            .map(mandate -> ((FundPensionOpeningMandateDetails) mandate.getDetails()).getPillar())
            .collect(Collectors.toSet());

    var partialWithdrawalMandatePillars =
        mandateBatchDto.getMandates().stream()
            .filter(mandate -> mandate.getMandateType() == PARTIAL_WITHDRAWAL)
            .map(mandate -> ((PartialWithdrawalMandateDetails) mandate.getDetails()).getPillar())
            .collect(Collectors.toSet());

    return Stream.concat(
            fundPensionOpeningMandatePillars.stream(), partialWithdrawalMandatePillars.stream())
        .collect(Collectors.toSet());
  }

  private MandateSignatureStatus persistFileSignedWithIdCard(
      User user,
      MandateBatch mandateBatch,
      IdCardSignatureSession session,
      String signedHashInHex) {
    byte[] signedFile = signService.getSignedFile(session, signedHashInHex);

    if (signedFile == null) { // TODO: use Optional
      throw new IllegalStateException("There is no signed file to persist");
    }

    persistSignedFile(mandateBatch, signedFile);
    startProcessingBatch(user, mandateBatch);
    return OUTSTANDING_TRANSACTION;
  }

  public MandateSignatureStatus persistIdCardSignedFileOrGetBatchProcessingStatus(
      Long userId,
      Long mandateBatchId,
      IdCardSignatureSession session,
      String signedHashInHex,
      Locale locale) {
    User user = userService.getById(userId);
    MandateBatch mandateBatch = getByIdAndUser(mandateBatchId, user).orElseThrow();

    if (!mandateBatch.isSigned()) {
      return persistFileSignedWithIdCard(user, mandateBatch, session, signedHashInHex);
    }

    return getMandateProcessingStatus(user, mandateBatch, locale);
  }

  public MobileIdSignatureSession mobileIdSign(
      Long mandateBatchId, Long userId, String phoneNumber) {
    User user = userService.getById(userId);
    List<SignatureFile> files = getMandateBatchContentFiles(mandateBatchId, user);

    return signService.startMobileIdSign(files, user.getPersonalCode(), phoneNumber);
  }

  private MandateSignatureStatus handleUnsignedMandateMobileId(
      User user, MandateBatch mandateBatch, MobileIdSignatureSession session) {
    return getStatus(user, mandateBatch, Optional.ofNullable(signService.getSignedFile(session)));
  }

  public MandateSignatureStatus finalizeMobileIdSignature(
      Long userId, Long mandateBatchId, MobileIdSignatureSession session, Locale locale) {
    User user = userService.getById(userId);
    MandateBatch mandateBatch = getByIdAndUser(mandateBatchId, user).orElseThrow();

    if (!mandateBatch.isSigned()) {
      return handleUnsignedMandateMobileId(user, mandateBatch, session);
    }

    return getMandateProcessingStatus(user, mandateBatch, locale);
  }

  private MandateSignatureStatus getMandateProcessingStatus(
      User user, MandateBatch mandateBatch, Locale locale) {

    var allMandatesHaveFinishedProcessing =
        mandateBatch.getMandates().stream()
            .allMatch(mandate -> mandateProcessor.isFinished(mandate));

    if (allMandatesHaveFinishedProcessing) {
      episService.clearCache(user);
      handleMandateProcessingErrors(mandateBatch);
      notifyAboutSignedMandate(user, mandateBatch, locale);
      return SIGNATURE;
    } else {
      return OUTSTANDING_TRANSACTION;
    }
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

  private MandateSignatureStatus handleUnsignedMandateSmartId(
      User user, MandateBatch mandateBatch, SmartIdSignatureSession session) {
    return getStatus(user, mandateBatch, Optional.ofNullable(signService.getSignedFile(session)));
  }

  private MandateSignatureStatus getStatus(
      User user, MandateBatch mandateBatch, Optional<byte[]> signedFile) {
    signedFile.ifPresent(
        it -> {
          persistSignedFile(mandateBatch, it);
          startProcessingBatch(user, mandateBatch);
        });

    return OUTSTANDING_TRANSACTION;
  }

  public void startProcessingBatch(User user, MandateBatch mandateBatch) {
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

  private MandateBatch persistSignedFile(MandateBatch mandateBatch, byte[] signedFile) {
    mandateBatch.setFile(signedFile);
    mandateBatch.setStatus(MandateBatchStatus.SIGNED);
    return mandateBatchRepository.save(mandateBatch);
  }
}
