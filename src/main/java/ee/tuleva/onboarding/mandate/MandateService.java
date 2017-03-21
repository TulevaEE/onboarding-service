package ee.tuleva.onboarding.mandate;

import com.codeborne.security.mobileid.IdCardSignatureSession;
import com.codeborne.security.mobileid.MobileIdSignatureSession;
import com.codeborne.security.mobileid.SignatureFile;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand;
import ee.tuleva.onboarding.mandate.command.CreateMandateCommandToMandateConverter;
import ee.tuleva.onboarding.mandate.content.MandateContentCreator;
import ee.tuleva.onboarding.mandate.exception.InvalidMandateException;
import ee.tuleva.onboarding.mandate.signature.SignatureService;
import ee.tuleva.onboarding.user.CsdUserPreferencesService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserPreferences;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

@Component
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class MandateService {

    private final MandateRepository mandateRepository;
	private final SignatureService signService;
	private final FundRepository fundRepository;
	private final MandateContentCreator mandateContentCreator;
	private final CsdUserPreferencesService csdUserPreferencesService;
    private final CreateMandateCommandToMandateConverter converter;

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
        List<SignatureFile> files = getMandateFiles(mandateId, user);
		return signService.startSign(files, user.getPersonalCode(), phoneNumber);
	}

	public IdCardSignatureSession idCardSign(Long mandateId, User user, String signingCertificate) {
		List<SignatureFile> files = getMandateFiles(mandateId, user);
		return signService.startSign(files, signingCertificate);
	}

    private List<SignatureFile> getMandateFiles(Long mandateId, User user) {
        Mandate mandate = mandateRepository.findByIdAndUser(mandateId, user);

        List<Fund> funds = new ArrayList<>();
        fundRepository.findAll().forEach(funds::add);

        UserPreferences userPreferences = csdUserPreferencesService.getPreferences(user.getPersonalCode());

        return mandateContentCreator.getContentFiles(user, mandate, funds, userPreferences)
                .stream()
                .map(file -> new SignatureFile(file.getName(), file.getMimeType(), file.getContent()))
                .collect(toList());
    }

    public String finalizeMobileIdSignature(Long mandateId, MobileIdSignatureSession session) {
		byte[] signedFile = signService.getSignedFile(session);

		if (signedFile != null) {
			persistSignedFile(mandateId, signedFile);
			return "SIGNATURE";
		} else {
			return "OUTSTANDING_TRANSACTION";
		}
	}

	public void finalizeIdCardSignature(Long mandateId, IdCardSignatureSession session, String signedHash) {
		byte[] signedFile = signService.getSignedFile(session, signedHash);
		if (signedFile != null) {
			persistSignedFile(mandateId, signedFile);
		} else {
			throw new IllegalStateException("There is no signed file to persist");
		}
	}

	private void persistSignedFile(Long mandateId, byte[] signedFile) {
		Mandate mandate = mandateRepository.findOne(mandateId);
		mandate.setMandate(signedFile);
		mandateRepository.save(mandate);
	}

}
