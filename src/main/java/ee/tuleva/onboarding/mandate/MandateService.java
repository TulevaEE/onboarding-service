package ee.tuleva.onboarding.mandate;

import static ee.tuleva.onboarding.mandate.application.ApplicationType.*;
import static java.util.Arrays.asList;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.application.ApplicationType;
import ee.tuleva.onboarding.mandate.builder.CreateMandateCommandToMandateConverter;
import ee.tuleva.onboarding.mandate.cancellation.CancellationMandateBuilder;
import ee.tuleva.onboarding.mandate.cancellation.InvalidApplicationTypeException;
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand;
import ee.tuleva.onboarding.mandate.command.CreateMandateCommandWrapper;
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import ee.tuleva.onboarding.mandate.event.BeforeMandateCreatedEvent;
import ee.tuleva.onboarding.mandate.exception.InvalidMandateException;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import ee.tuleva.onboarding.mandate.signature.SignatureFile;
import ee.tuleva.onboarding.mandate.signature.SignatureService;
import ee.tuleva.onboarding.mandate.signature.idcard.IdCardSignatureSession;
import ee.tuleva.onboarding.mandate.signature.mobileid.MobileIdSignatureSession;
import ee.tuleva.onboarding.mandate.signature.smartid.SmartIdSignatureSession;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MandateService {

  private static final String OUTSTANDING_TRANSACTION = "OUTSTANDING_TRANSACTION";
  private static final String SIGNATURE = "SIGNATURE";

  private final MandateRepository mandateRepository;
  private final SignatureService signService;
  private final CreateMandateCommandToMandateConverter mandateConverter;
  private final MandateProcessorService mandateProcessor;
  private final CancellationMandateBuilder cancellationMandateBuilder;
  private final MandateFileService mandateFileService;
  private final UserService userService;
  private final EpisService episService;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final UserConversionService conversionService;
  private final MandateValidator mandateValidator;

  public Mandate save(
      AuthenticatedPerson authenticatedPerson, CreateMandateCommand createMandateCommand) {
    mandateValidator.validate(createMandateCommand, authenticatedPerson);
    User user = userService.getById(authenticatedPerson.getUserId());
    ConversionResponse conversion = conversionService.getConversion(user);
    ContactDetails contactDetails = episService.getContactDetails(user);
    CreateMandateCommandWrapper wrapper =
        new CreateMandateCommandWrapper(
            createMandateCommand, authenticatedPerson, user, conversion, contactDetails);
    Mandate mandate = mandateConverter.convert(wrapper);
    return save(user, mandate);
  }

  public Mandate saveCancellation(
      AuthenticatedPerson authenticatedPerson, ApplicationDTO applicationToCancel) {
    ApplicationType applicationTypeToCancel = applicationToCancel.getType();
    if (!asList(WITHDRAWAL, EARLY_WITHDRAWAL, TRANSFER).contains(applicationTypeToCancel)) {
      throw new InvalidApplicationTypeException(
          "Invalid application type: " + applicationTypeToCancel);
    }

    User user = userService.getById(authenticatedPerson.getUserId());
    ConversionResponse conversion = conversionService.getConversion(user);
    ContactDetails contactDetails = episService.getContactDetails(user);
    Mandate mandate =
        cancellationMandateBuilder.build(
            applicationToCancel, authenticatedPerson, user, conversion, contactDetails);
    return save(user, mandate);
  }

  public Mandate save(User user, Mandate mandate) {
    log.info("Saving mandate for user {}", user.getId());
    applicationEventPublisher.publishEvent(new BeforeMandateCreatedEvent(this, user, mandate));
    return mandateRepository.save(mandate);
  }

  public MobileIdSignatureSession mobileIdSign(Long mandateId, Long userId, String phoneNumber) {
    User user = userService.getById(userId);
    List<SignatureFile> files = mandateFileService.getMandateFiles(mandateId, userId);
    return signService.startMobileIdSign(files, user.getPersonalCode(), phoneNumber);
  }

  public SmartIdSignatureSession smartIdSign(Long mandateId, Long userId) {
    User user = userService.getById(userId);
    List<SignatureFile> files = mandateFileService.getMandateFiles(mandateId, userId);
    return signService.startSmartIdSign(files, user.getPersonalCode());
  }

  public String finalizeSmartIdSignature(
      Long userId, Long mandateId, SmartIdSignatureSession session, Locale locale) {
    User user = userService.getById(userId);
    Mandate mandate = mandateRepository.findByIdAndUserId(mandateId, userId);

    if (mandate.isSigned()) {
      return handleSignedMandate(user, mandate, locale);
    } else {
      return handleUnsignedMandateSmartId(user, mandate, session);
    }
  }

  private String handleUnsignedMandateSmartId(
      User user, Mandate mandate, SmartIdSignatureSession session) {
    return getStatus(user, mandate, signService.getSignedFile(session));
  }

  private String getStatus(User user, Mandate mandate, byte[] signedFile) {
    if (signedFile != null) {
      persistSignedFile(mandate, signedFile);
      mandateProcessor.start(user, mandate);
    }
    return OUTSTANDING_TRANSACTION;
  }

  public IdCardSignatureSession idCardSign(Long mandateId, Long userId, String signingCertificate) {
    List<SignatureFile> files = mandateFileService.getMandateFiles(mandateId, userId);
    return signService.startIdCardSign(files, signingCertificate);
  }

  public String finalizeMobileIdSignature(
      Long userId, Long mandateId, MobileIdSignatureSession session, Locale locale) {
    User user = userService.getById(userId);
    Mandate mandate = mandateRepository.findByIdAndUserId(mandateId, userId);

    if (mandate.isSigned()) {
      return handleSignedMandate(user, mandate, locale);
    } else {
      return handleUnsignedMandateMobileId(user, mandate, session);
    }
  }

  private String handleUnsignedMandateMobileId(
      User user, Mandate mandate, MobileIdSignatureSession session) {
    return getStatus(user, mandate, signService.getSignedFile(session));
  }

  public String finalizeIdCardSignature(
      Long userId,
      Long mandateId,
      IdCardSignatureSession session,
      String signedHashInHex,
      Locale locale) {
    User user = userService.getById(userId);
    Mandate mandate = mandateRepository.findByIdAndUserId(mandateId, userId);

    if (mandate.isSigned()) {
      return handleSignedMandate(user, mandate, locale);
    } else {
      return handleUnsignedMandateIdCard(user, mandate, session, signedHashInHex);
    }
  }

  public Mandate get(Long id) {
    return mandateRepository.findById(id).orElseThrow(IllegalStateException::new);
  }

  private String handleSignedMandate(User user, Mandate mandate, Locale locale) {
    if (mandateProcessor.isFinished(mandate)) {
      episService.clearCache(user);
      handleMandateProcessingErrors(mandate);
      notifyAboutSignedMandate(user, mandate, locale);
      return SIGNATURE;
    } else {
      return OUTSTANDING_TRANSACTION;
    }
  }

  private void handleMandateProcessingErrors(Mandate mandate) {
    ErrorsResponse errorsResponse = mandateProcessor.getErrors(mandate);

    log.info("Mandate processing errors {}", errorsResponse);
    if (errorsResponse.hasErrors()) {
      throw new InvalidMandateException(errorsResponse);
    }
  }

  private String handleUnsignedMandateIdCard(
      User user, Mandate mandate, IdCardSignatureSession session, String signedHashInHex) {
    byte[] signedFile = signService.getSignedFile(session, signedHashInHex);
    if (signedFile != null) { // TODO: use Optional
      persistSignedFile(mandate, signedFile);
      mandateProcessor.start(user, mandate);
      return OUTSTANDING_TRANSACTION;
    } else {
      throw new IllegalStateException("There is no signed file to persist");
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
