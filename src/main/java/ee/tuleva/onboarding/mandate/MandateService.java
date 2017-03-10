package ee.tuleva.onboarding.mandate;

import com.codeborne.security.mobileid.MobileIdSignatureFile;
import com.codeborne.security.mobileid.MobileIdSignatureSession;
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
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class MandateService {

    private final MandateRepository mandateRepository;
	private final MobileIdSignService signService;
	private final FundRepository fundRepository;
	private final MandateContentCreator mandateContentCreator;
	private final CsdUserPreferencesService csdUserPreferencesService;

    CreateMandateCommandToMandateConverter converter = new CreateMandateCommandToMandateConverter();

    public Mandate save(User user, CreateMandateCommand createMandateCommand) {

        Mandate mandate = converter.convert(createMandateCommand);
        mandate.setUser(user);

        return mandateRepository.save(mandate);
    }

	public MobileIdSignatureSession sign(Long mandateId, User user, String phoneNumber) {
		Mandate mandate = mandateRepository.findByIdAndUser(mandateId, user);

		List<Fund> funds = new ArrayList<>();
		fundRepository.findAll().forEach(funds::add);

		UserPreferences userPreferences = csdUserPreferencesService.getPreferences(user.getPersonalCode());

		List<MobileIdSignatureFile> files = mandateContentCreator.getContentFiles(user, mandate, funds, userPreferences)
				.stream()
				.map(contentFile -> new MobileIdSignatureFile(contentFile.getName(), contentFile.getMimeType(), contentFile.getContent()))
				.collect(Collectors.toList());

		return signService.startSignFiles(files, user.getPersonalCode(), phoneNumber);
	}

	public String getSignatureStatus(Long mandateId, MandateSignatureSession session) {
		MobileIdSignatureSession mobileIdSignatureSession = new MobileIdSignatureSession(session.getSessCode());
		byte[] signedFile = signService.getSignedFile(mobileIdSignatureSession);

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
