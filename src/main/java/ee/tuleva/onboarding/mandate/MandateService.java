package ee.tuleva.onboarding.mandate;

import com.codeborne.security.mobileid.MobileIdSignatureSession;
import com.codeborne.security.mobileid.SignatureFile;
import ee.tuleva.domain.fund.Fund;
import ee.tuleva.domain.fund.FundRepository;
import ee.tuleva.onboarding.mandate.content.MandateContentCreator;
import ee.tuleva.onboarding.sign.MobileIdSignService;
import ee.tuleva.onboarding.user.CsdUserPreferencesService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserPreferences;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Component
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class MandateService {

    private final MandateRepository mandateRepository;
	private final MobileIdSignService signService;
	private final FundRepository fundRepository;
	private final MandateContentCreator mandateContentCreator;
	private final CsdUserPreferencesService csdUserPreferencesService;
    private final CreateMandateCommandToMandateConverter converter;

    public Mandate save(User user, CreateMandateCommand createMandateCommand) {

        Mandate mandate = converter.convert(createMandateCommand);
        mandate.setUser(user);

        return mandateRepository.save(mandate);
    }

	public MobileIdSignatureSession mobileIdSign(Long mandateId, User user, String phoneNumber) {
        List<SignatureFile> files = getMandateFiles(mandateId, user);
		return signService.startSign(files, user.getPersonalCode(), phoneNumber);
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

    public String getSignatureStatus(Long mandateId, MobileIdSignatureSession session) {
		byte[] signedFile = signService.getSignedFile(session);

		if (signedFile != null) {
			Mandate mandate = mandateRepository.findOne(mandateId);
			mandate.setMandate(signedFile);
			mandateRepository.save(mandate);

			return "SIGNATURE";
		} else {
			return "OUTSTANDING_TRANSACTION";
		}
	}
}
