package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.mandate.batch.MandateBatchSignatureStatus.*;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchSignatureStatus.SIGNATURE;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.error.response.ErrorResponse;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.MandateFileService;
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import ee.tuleva.onboarding.mandate.exception.MandateProcessingException;
import ee.tuleva.onboarding.mandate.generic.GenericMandateService;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import ee.tuleva.onboarding.mandate.signature.SignatureFile;
import ee.tuleva.onboarding.mandate.signature.SignatureService;
import ee.tuleva.onboarding.mandate.signature.smartid.SmartIdSignatureSession;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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

  public MandateBatchSignatureStatus finalizeSmartIdSignature(
      Long userId, Long mandateBatchId, SmartIdSignatureSession session, Locale locale) {
    User user = userService.getById(userId);
    MandateBatch mandateBatch = getByIdAndUser(mandateBatchId, user).orElseThrow();

    if (mandateBatch.isSigned()) {
      return handleSignedMandate(user, mandateBatch, locale);
    } else {
      return handleUnsignedMandateSmartId(user, mandateBatch, session);
    }
  }

  private MandateBatchSignatureStatus handleSignedMandate(
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

  private MandateBatchSignatureStatus handleUnsignedMandateSmartId(
      User user, MandateBatch mandateBatch, SmartIdSignatureSession session) {
    return getStatus(user, mandateBatch, Optional.ofNullable(signService.getSignedFile(session)));
  }

  private MandateBatchSignatureStatus getStatus(
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
  }

  private MandateBatch persistSignedFile(MandateBatch mandateBatch, byte[] signedFile) {
    mandateBatch.setFile(signedFile);
    mandateBatch.setStatus(MandateBatchStatus.SIGNED);
    return mandateBatchRepository.save(mandateBatch);
  }
}
