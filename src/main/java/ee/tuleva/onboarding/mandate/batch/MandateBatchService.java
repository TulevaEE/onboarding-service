package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.mandate.MandateService.OUTSTANDING_TRANSACTION;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.mandate.MandateFileService;
import ee.tuleva.onboarding.mandate.generic.GenericMandateService;
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
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MandateBatchService {
  private final MandateBatchRepository mandateBatchRepository;
  private final MandateFileService mandateFileService;
  private final GenericMandateService genericMandateService;
  private UserService userService;
  private SignatureService signService;

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

  public SmartIdSignatureSession smartIdSign(Long mandateId, Long userId) {
    User user = userService.getById(userId);
    List<SignatureFile> files = mandateFileService.getMandateFiles(mandateId, userId);
    return signService.startSmartIdSign(files, user.getPersonalCode());
  }

  public MandateBatchStatus finalizeSmartIdSignature(
      Long userId, Long mandateId, SmartIdSignatureSession session, Locale locale) {
    User user = userService.getById(userId);
    MandateBatch mandateBatch = getByIdAndUser(mandateId, user).orElseThrow();

    if (mandateBatch.isSigned()) {
      return handleSignedMandate(user, mandateBatch, locale);
    } else {
      return handleUnsignedMandateSmartId(user, mandateBatch, session);
    }
  }

  private MandateBatchStatus handleSignedMandate(
      User user, MandateBatch mandateBatch, Locale locale) {
    if (mandateProcessor.isFinished(mandate)) {
      episService.clearCache(user);
      handleMandateProcessingErrors(mandate);
      notifyAboutSignedMandate(user, mandate, locale);
      return SIGNATURE;
    } else {
      return OUTSTANDING_TRANSACTION;
    }
  }

  private MandateBatchStatus handleUnsignedMandateSmartId(
      User user, MandateBatch mandateBatch, SmartIdSignatureSession session) {
    return getStatus(user, mandateBatch, signService.getSignedFile(session));
  }

  private MandateBatchStatus getStatus(User user, MandateBatch mandateBatch, byte[] signedFile) {
    if (signedFile != null) {
      MandateBatch savedBatch = persistSignedFile(mandateBatch, signedFile);

      // TODO start processing
      mandateProcessor.start(user, mandate);

      return savedBatch.getStatus();
    }
    return mandateBatch.getStatus();
  }

  private MandateBatch persistSignedFile(MandateBatch mandateBatch, byte[] signedFile) {
    mandateBatch.setFile(signedFile);
    mandateBatch.setStatus(MandateBatchStatus.SIGNED);
    return mandateBatchRepository.save(mandateBatch);
  }
}
