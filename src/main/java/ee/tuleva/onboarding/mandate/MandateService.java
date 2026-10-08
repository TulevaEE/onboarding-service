package ee.tuleva.onboarding.mandate;

import static ee.tuleva.onboarding.applicationtype.ApplicationType.*;
import static ee.tuleva.onboarding.signature.SignatureStatus.OUTSTANDING_TRANSACTION;
import static ee.tuleva.onboarding.signature.SignatureStatus.SIGNATURE;
import static java.util.Arrays.asList;

import ee.tuleva.onboarding.applicationtype.ApplicationType;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.mandate.application.ApplicationSnapshot;
import ee.tuleva.onboarding.mandate.builder.CreateMandateCommandToMandateConverter;
import ee.tuleva.onboarding.mandate.cancellation.CancellationMandateBuilder;
import ee.tuleva.onboarding.mandate.cancellation.InvalidApplicationTypeException;
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand;
import ee.tuleva.onboarding.mandate.command.CreateMandateCommandWrapper;
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import ee.tuleva.onboarding.mandate.event.BeforeMandateCreatedEvent;
import ee.tuleva.onboarding.mandate.exception.MandateProcessingException;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import ee.tuleva.onboarding.signature.IdCardSignatureSession;
import ee.tuleva.onboarding.signature.MobileIdSignatureSession;
import ee.tuleva.onboarding.signature.SignableEntity;
import ee.tuleva.onboarding.signature.SignatureFile;
import ee.tuleva.onboarding.signature.SignatureService;
import ee.tuleva.onboarding.signature.SignatureStateException;
import ee.tuleva.onboarding.signature.SignatureStatus;
import ee.tuleva.onboarding.signature.SmartIdSignatureSession;
import ee.tuleva.onboarding.signature.StartIdCardSignCommand;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MandateService {

  private final MandateRepository mandateRepository;
  private final SignatureService signService;
  private final CreateMandateCommandToMandateConverter mandateConverter;
  private final MandateProcessorService mandateProcessor;
  private final CancellationMandateBuilder cancellationMandateBuilder;
  private final MandateFileService mandateFileService;
  private final UserService userService;
  private final MandateContacts mandateContacts;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final UserConversionService conversionService;
  private final MandateValidator mandateValidator;
  private final LocaleService localeService;

  public Mandate save(
      AuthenticatedPerson authenticatedPerson, CreateMandateCommand createMandateCommand) {
    mandateValidator.validate(createMandateCommand, authenticatedPerson);
    User user = userService.getById(authenticatedPerson.getUserIdOrThrow()).orElseThrow();
    ConversionResponse conversion = conversionService.getConversion(user);
    MandateContactDetails contactDetails = mandateContacts.getContactDetails(user);
    CreateMandateCommandWrapper wrapper =
        new CreateMandateCommandWrapper(
            createMandateCommand, authenticatedPerson, user, conversion, contactDetails);
    Mandate mandate = mandateConverter.convert(wrapper);
    return save(user, mandate);
  }

  public Mandate saveCancellation(
      AuthenticatedPerson authenticatedPerson, ApplicationSnapshot applicationToCancel) {
    ApplicationType applicationTypeToCancel = applicationToCancel.getType();
    if (!asList(WITHDRAWAL, EARLY_WITHDRAWAL, TRANSFER).contains(applicationTypeToCancel)) {
      throw new InvalidApplicationTypeException(
          "Invalid application type: " + applicationTypeToCancel);
    }

    User user = userService.getById(authenticatedPerson.getUserIdOrThrow()).orElseThrow();
    ConversionResponse conversion = conversionService.getConversion(user);
    MandateContactDetails contactDetails = mandateContacts.getContactDetails(user);
    Mandate mandate =
        cancellationMandateBuilder.build(
            applicationToCancel, authenticatedPerson, user, conversion, contactDetails);
    return save(user, mandate);
  }

  public Mandate save(User user, Mandate mandate) {
    log.info("Saving mandate for user {}", user.getId());
    mandate.setLocale(Locale.of(localeService.getCurrentLanguage()));
    applicationEventPublisher.publishEvent(new BeforeMandateCreatedEvent(this, user, mandate));
    return mandateRepository.save(mandate);
  }

  public MobileIdSignatureSession mobileIdSign(Long mandateId, Long userId, String phoneNumber) {
    User user = userService.getById(userId).orElseThrow();
    List<SignatureFile> files = mandateFileService.getMandateFiles(mandateId, userId);
    return signService.startMobileIdSign(files, user.getPersonalCode(), phoneNumber);
  }

  public SmartIdSignatureSession smartIdSign(
      Long mandateId, AuthenticatedPerson authenticatedPerson) {
    List<SignatureFile> files =
        mandateFileService.getMandateFiles(mandateId, authenticatedPerson.getUserIdOrThrow());
    return signService.startSmartIdSign(files, authenticatedPerson);
  }

  public SignatureStatus finalizeSmartIdSignature(
      Long userId, Long mandateId, SmartIdSignatureSession session, Locale locale) {
    User user = userService.getById(userId).orElseThrow();
    Mandate mandate = mandateRepository.findByIdAndUserId(mandateId, userId);

    if (mandate.isSigned()) {
      return statusOfSignedMandate(mandate);
    }
    return persistIfSigned(user, mandate, signService.getSignedFile(session), locale);
  }

  private SignatureStatus persistIfSigned(
      User user, Mandate mandate, byte @Nullable [] signedFile, Locale locale) {
    if (signedFile == null) {
      return OUTSTANDING_TRANSACTION;
    }
    return persistAndProcess(user, mandate, signedFile, locale);
  }

  private SignatureStatus persistAndProcess(
      User user, Mandate mandate, byte[] signedFile, Locale locale) {
    persistSignedFile(mandate, signedFile);
    mandateProcessor.start(user, mandate);
    if (!mandateProcessor.isFinished(mandate)) {
      return OUTSTANDING_TRANSACTION;
    }
    mandateContacts.clearCache(user);
    handleMandateProcessingErrors(mandate);
    notifyAboutSignedMandate(user, mandate, locale);
    return SIGNATURE;
  }

  public IdCardSignatureSession idCardSign(
      Long mandateId, Long userId, StartIdCardSignCommand signCommand) {
    User user = userService.getById(userId).orElseThrow();
    List<SignatureFile> files = mandateFileService.getMandateFiles(mandateId, userId);
    return signService.startIdCardSign(
        signableMandate(mandateId),
        files,
        signCommand.certificate(),
        signCommand.supportedHashFunctions(),
        user.getPersonalCode());
  }

  public SignatureStatus finalizeMobileIdSignature(
      Long userId, Long mandateId, MobileIdSignatureSession session, Locale locale) {
    User user = userService.getById(userId).orElseThrow();
    Mandate mandate = mandateRepository.findByIdAndUserId(mandateId, userId);

    if (mandate.isSigned()) {
      return statusOfSignedMandate(mandate);
    }
    return persistIfSigned(user, mandate, signService.getSignedFile(session), locale);
  }

  public SignatureStatus persistIdCardSignature(
      Long userId,
      Long mandateId,
      IdCardSignatureSession session,
      String signature,
      Locale locale) {
    User user = userService.getById(userId).orElseThrow();
    Mandate mandate = mandateRepository.findByIdAndUserId(mandateId, userId);

    if (mandate.isSigned()) {
      throw SignatureStateException.alreadySigned("Mandate", mandateId);
    }
    byte[] signedFile = signService.getSignedFile(session, signableMandate(mandateId), signature);
    return persistAndProcess(user, mandate, signedFile, locale);
  }

  private static SignableEntity signableMandate(Long mandateId) {
    return new SignableEntity("Mandate", mandateId);
  }

  public SignatureStatus getIdCardSignatureStatus(Long userId, Long mandateId) {
    Mandate mandate = mandateRepository.findByIdAndUserId(mandateId, userId);

    if (!mandate.isSigned()) {
      throw SignatureStateException.notSigned("Mandate", mandateId);
    }
    return statusOfSignedMandate(mandate);
  }

  public Mandate get(Long id) {
    return mandateRepository.findById(id).orElseThrow(IllegalStateException::new);
  }

  private SignatureStatus statusOfSignedMandate(Mandate mandate) {
    if (!mandateProcessor.isFinished(mandate)) {
      return OUTSTANDING_TRANSACTION;
    }
    handleMandateProcessingErrors(mandate);
    return SIGNATURE;
  }

  private void handleMandateProcessingErrors(Mandate mandate) {
    ErrorsResponse errorsResponse = mandateProcessor.getErrors(mandate);

    log.info("Mandate processing errors {}", errorsResponse);
    if (errorsResponse.hasErrors()) {
      throw new MandateProcessingException(errorsResponse);
    }
  }

  private void notifyAboutSignedMandate(User user, Mandate mandate, Locale locale) {
    applicationEventPublisher.publishEvent(
        new AfterMandateSignedEvent(this, user, mandate, locale));
  }

  private void persistSignedFile(Mandate mandate, byte[] signedFile) {
    mandate.setMandate(signedFile);
    mandateRepository.save(mandate);
  }
}
