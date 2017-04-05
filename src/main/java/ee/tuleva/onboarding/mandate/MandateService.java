package ee.tuleva.onboarding.mandate;

import com.codeborne.security.mobileid.IdCardSignatureSession;
import com.codeborne.security.mobileid.MobileIdSignatureSession;
import com.codeborne.security.mobileid.SignatureFile;
import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand;
import ee.tuleva.onboarding.mandate.command.CreateMandateCommandToMandateConverter;
import ee.tuleva.onboarding.mandate.email.EmailService;
import ee.tuleva.onboarding.mandate.exception.InvalidMandateException;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import ee.tuleva.onboarding.mandate.signature.SignatureService;
import ee.tuleva.onboarding.mandate.statistics.FundTransferStatisticsService;
import ee.tuleva.onboarding.mandate.statistics.FundValueStatistics;
import ee.tuleva.onboarding.mandate.statistics.FundValueStatisticsRepository;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MandateService {

    private final MandateRepository mandateRepository;
	private final SignatureService signService;
    private final CreateMandateCommandToMandateConverter converter;
	private final EmailService emailService;
	private final FundValueStatisticsRepository fundValueStatisticsRepository;
	private final FundTransferStatisticsService fundTransferStatisticsService;
	private final MandateProcessorService mandateProcessor;
	private final MandateFileService mandateFileService;

    public Mandate save(User user, CreateMandateCommand createMandateCommand) {
		validateCreateMandateCommand(createMandateCommand);
        Mandate mandate = converter.convert(createMandateCommand);
        mandate.setUser(user);

        return mandateRepository.save(mandate);
    }

	private void validateCreateMandateCommand(CreateMandateCommand createMandateCommand) {
		if(countValuesBiggerThanOne(summariseSourceFundTransferAmounts(createMandateCommand)) > 0) {
			throw new InvalidMandateException();
		};
	}

	private Map<String, BigDecimal> summariseSourceFundTransferAmounts(CreateMandateCommand createMandateCommand) {
		Map<String, BigDecimal> summaryMap = new HashMap<>();

		createMandateCommand.getFundTransferExchanges().forEach( fte -> {
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
		return summaryMap.values().stream().filter( value -> value.compareTo(BigDecimal.ONE) > 0).count();
	}

	public MobileIdSignatureSession mobileIdSign(Long mandateId, User user, String phoneNumber) {
        List<SignatureFile> files = mandateFileService.getMandateFiles(mandateId, user);
		return signService.startSign(files, user.getPersonalCode(), phoneNumber);
	}

	public IdCardSignatureSession idCardSign(Long mandateId, User user, String signingCertificate) {
		List<SignatureFile> files = mandateFileService.getMandateFiles(mandateId, user);
		return signService.startSign(files, signingCertificate);
	}

    public String finalizeMobileIdSignature(User user, UUID statisticsIdentifier, Long mandateId, MobileIdSignatureSession session) {

		Mandate mandate = mandateRepository.findByIdAndUser(mandateId, user);

		if(isMandateSigned(mandate)) {
			return handleSignedMandate(user, mandate, statisticsIdentifier);
		} else {
			return handleUnsignedMandateMobileId(user, mandate, session);
		}
	}

	private String handleUnsignedMandateMobileId(User user, Mandate mandate, MobileIdSignatureSession session) {
		byte[] signedFile = signService.getSignedFile(session);

		if (signedFile != null) {
			persistSignedFile(mandate, signedFile);
			mandateProcessor.start(user, mandate);
			return "OUTSTANDING_TRANSACTION"; // TODO: use enum
		} else {
			return "OUTSTANDING_TRANSACTION"; // TODO: use enum
		}
	}

	public String finalizeIdCardSignature(User user, UUID statisticsIdentifier, Long mandateId, IdCardSignatureSession session, String signedHash) {

		Mandate mandate = mandateRepository.findByIdAndUser(mandateId, user);

		if(isMandateSigned(mandate)) {
			return handleSignedMandate(user, mandate, statisticsIdentifier);
		} else {
			return handleUnsignedMandateIdCard(user, mandate, session, signedHash);
		}
	}

	private boolean isMandateSigned(Mandate mandate) {
		return mandate.getMandate().isPresent();
	}

	private String handleSignedMandate(User user, Mandate mandate, UUID statisticsIdentifier) {
		if(mandateProcessor.isFinished(mandate)) {
			handleMandateProcessingErrors(mandate);
			persistFundTransferExchangeStatistics(user, statisticsIdentifier, mandate);
//				notifyMandateProcessor(user, mandateId, signedFile);
			return "SIGNATURE"; // TODO: use enum
		} else {
			return "OUTSTANDING_TRANSACTION"; // TODO: use enum
		}
	}

	private void handleMandateProcessingErrors(Mandate mandate) {
		ErrorsResponse errorsResponse = mandateProcessor.getErrors(mandate);

		if(errorsResponse.hasErrors()) {
			throw new ErrorsResponseException(errorsResponse);
		}
	}

	private String handleUnsignedMandateIdCard(User user, Mandate mandate, IdCardSignatureSession session, String signedHash) {
		byte[] signedFile = signService.getSignedFile(session, signedHash);
		if (signedFile != null) {
			persistSignedFile(mandate, signedFile);
			mandateProcessor.start(user, mandate);
			return "OUTSTANDING_TRANSACTION"; // TODO: use enum
		} else {
			throw new IllegalStateException("There is no signed file to persist");
		}
	}

	private void notifyMandateProcessor(User user, Long mandateId, byte[] signedFile) {
		emailService.send(user, mandateId, signedFile);
	}

	private void persistSignedFile(Mandate mandate, byte[] signedFile) {
		mandate.setMandate(signedFile);
		mandateRepository.save(mandate);
	}

	private void persistFundTransferExchangeStatistics(User user, UUID statisticsIdentifier, Mandate mandate) {
		List<FundValueStatistics> fundValueStatisticsList = fundValueStatisticsRepository.findByIdentifier(statisticsIdentifier);
		fundTransferStatisticsService.addFrom(mandate, fundValueStatisticsList);

		// TODO: decide if we need to delete fund value statistics after adding or it might be needed in the same session
		// to generate an other mandate and then be ereased by a chron job
//		fundValueStatisticsList.forEach( fundValueStatistics -> {
//			fundValueStatisticsRepository.delete(fundValueStatistics);
//		});
	}

}
