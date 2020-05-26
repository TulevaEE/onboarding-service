package ee.tuleva.onboarding.mandate;

import com.codeborne.security.mobileid.IdCardSignatureSession;
import com.codeborne.security.mobileid.MobileIdSignatureSession;
import com.codeborne.security.mobileid.SignatureFile;
import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand;
import ee.tuleva.onboarding.mandate.command.CreateMandateCommandToMandateConverter;
import ee.tuleva.onboarding.mandate.command.CreateMandateCommandWithUser;
import ee.tuleva.onboarding.mandate.exception.InvalidMandateException;
import ee.tuleva.onboarding.mandate.listener.MandateCreatedEvent;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import ee.tuleva.onboarding.mandate.signature.SignatureService;
import ee.tuleva.onboarding.mandate.signature.SmartIdSignatureSession;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.servlet.LocaleResolver;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private final MandateFileService mandateFileService;
    private final UserService userService;
    private final EpisService episService;
    private final AmlService amlService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final HttpServletRequest request;
    private final LocaleResolver localeResolver;

    public Mandate save(Long userId, CreateMandateCommand createMandateCommand) {
        validateCreateMandateCommand(createMandateCommand);
        User user = userService.getById(userId);
        Mandate mandate = mandateConverter.convert(new CreateMandateCommandWithUser(createMandateCommand, user));
        UserPreferences userPreferences = episService.getContactDetails(user);
        amlService.addPensionRegistryNameCheckIfMissing(user, userPreferences);
        log.info("Saving mandate {}", mandate);
        if (!amlService.allChecksPassed(mandate)) {
            throw InvalidMandateException.amlChecksMissing();
        }
        return mandateRepository.save(mandate);
    }

    private void validateCreateMandateCommand(CreateMandateCommand createMandateCommand) {
        if (countValuesBiggerThanOne(summariseSourceFundTransferAmounts(createMandateCommand)) > 0) {
            throw InvalidMandateException.sourceAmountExceeded();
        }

        if (isSameSourceToTargetTransferPresent(createMandateCommand)) {
            throw InvalidMandateException.sameSourceAndTargetTransferPresent();
        }

    }

    private boolean isSameSourceToTargetTransferPresent(CreateMandateCommand createMandateCommand) {
        return createMandateCommand.getFundTransferExchanges().stream()
            .anyMatch(fte -> fte.getSourceFundIsin().equalsIgnoreCase(fte.getTargetFundIsin()));
    }

    private Map<String, BigDecimal> summariseSourceFundTransferAmounts(CreateMandateCommand createMandateCommand) {
        Map<String, BigDecimal> summaryMap = new HashMap<>();

        createMandateCommand.getFundTransferExchanges().forEach(fte -> {
            if (!summaryMap.containsKey(fte.getSourceFundIsin())) {
                summaryMap.put(fte.getSourceFundIsin(), new BigDecimal(0));
            }

            summaryMap.put(
                fte.getSourceFundIsin(),
                summaryMap.get(fte.getSourceFundIsin()).add(fte.getAmount())
            );
        });

        return summaryMap;
    }

    private long countValuesBiggerThanOne(Map<String, BigDecimal> summaryMap) {
        return summaryMap.values().stream().filter(value -> value.compareTo(BigDecimal.ONE) > 0).count();
    }

    public MobileIdSignatureSession mobileIdSign(Long mandateId, Long userId, String phoneNumber) {
        User user = userService.getById(userId);
        List<SignatureFile> files = mandateFileService.getMandateFiles(mandateId, userId);
        return signService.startSign(files, user.getPersonalCode(), phoneNumber);
    }

    public SmartIdSignatureSession smartIdSign(Long mandateId, Long userId) {
        User user = userService.getById(userId);
        List<SignatureFile> files = mandateFileService.getMandateFiles(mandateId, userId);
        return signService.startSmartIdSign(files, user.getPersonalCode());
    }

    public String finalizeSmartIdSignature(Long userId, Long mandateId, SmartIdSignatureSession session) {
        User user = userService.getById(userId);
        Mandate mandate = mandateRepository.findByIdAndUserId(mandateId, userId);

        if (isMandateSigned(mandate)) {
            return handleSignedMandate(user, mandate);
        } else {
            return handleUnsignedMandateSmartId(user, mandate, session);
        }
    }

    private String handleUnsignedMandateSmartId(User user, Mandate mandate, SmartIdSignatureSession session) {
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
        return signService.startSign(files, signingCertificate);
    }

    public String finalizeMobileIdSignature(Long userId, Long mandateId, MobileIdSignatureSession session) {
        User user = userService.getById(userId);
        Mandate mandate = mandateRepository.findByIdAndUserId(mandateId, userId);

        if (isMandateSigned(mandate)) {
            return handleSignedMandate(user, mandate);
        } else {
            return handleUnsignedMandateMobileId(user, mandate, session);
        }
    }

    private String handleUnsignedMandateMobileId(User user, Mandate mandate, MobileIdSignatureSession session) {
        return getStatus(user, mandate, signService.getSignedFile(session));
    }

    public String finalizeIdCardSignature(Long userId, Long mandateId, IdCardSignatureSession session, String signedHash) {
        User user = userService.getById(userId);
        Mandate mandate = mandateRepository.findByIdAndUserId(mandateId, userId);

        if (isMandateSigned(mandate)) {
            return handleSignedMandate(user, mandate);
        } else {
            return handleUnsignedMandateIdCard(user, mandate, session, signedHash);
        }
    }

    private boolean isMandateSigned(Mandate mandate) {
        return mandate.getMandate().isPresent();
    }

    private String handleSignedMandate(User user, Mandate mandate) {
        if (mandateProcessor.isFinished(mandate)) {
            episService.clearCache(user);
            handleMandateProcessingErrors(mandate);
            notifyAboutSignedMandate(user,
                mandate.getId(),
                mandate.getMandate()
                    .orElseThrow(() -> new RuntimeException("Expecting mandate to be signed, but can not access signed file.")),
                mandate.getPillar()
            );

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

    private String handleUnsignedMandateIdCard(User user, Mandate mandate, IdCardSignatureSession session, String signedHash) {
        byte[] signedFile = signService.getSignedFile(session, signedHash);
        if (signedFile != null) { // TODO: use Optional
            persistSignedFile(mandate, signedFile);
            mandateProcessor.start(user, mandate);
            return OUTSTANDING_TRANSACTION;
        } else {
            throw new IllegalStateException("There is no signed file to persist");
        }
    }

    private void notifyAboutSignedMandate(User user, Long mandateId, byte[] signedFile, int pillar) {
        Locale locale = localeResolver.resolveLocale(request);
        MandateCreatedEvent event = MandateCreatedEvent.newEvent(
            user,
            mandateId,
            signedFile,
            pillar,
            locale
        );
        applicationEventPublisher.publishEvent(event);
    }

    private void persistSignedFile(Mandate mandate, byte[] signedFile) {
        mandate.setMandate(signedFile);
        mandateRepository.save(mandate);
    }

}
