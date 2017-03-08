package ee.tuleva.onboarding.mandate;

import com.codeborne.security.mobileid.MobileIdSignatureFile;
import com.codeborne.security.mobileid.MobileIdSignatureSession;
import ee.tuleva.domain.fund.Fund;
import ee.tuleva.domain.fund.FundRepository;
import ee.tuleva.onboarding.mandate.content.HtmlMandateContentCreator;
import ee.tuleva.onboarding.mandate.content.MandateContentCreator;
import ee.tuleva.onboarding.mandate.content.MandateContentFile;
import ee.tuleva.onboarding.sign.MobileIdSignService;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class MandateService {

    private final MandateRepository mandateRepository;
	private final MobileIdSignService signService;
	private final FundRepository fundRepository;
	private final MandateContentCreator mandateContentCreator;

    CreateMandateCommandToMandateConverter converter = new CreateMandateCommandToMandateConverter();

    public Mandate save(User user, CreateMandateCommand createMandateCommand) {

        Mandate mandate = converter.convert(createMandateCommand);
        mandate.setUser(user);

        return mandateRepository.save(mandate);
    }

	public MobileIdSignatureSession sign(Long mandateId, User user) {
		Mandate mandate = mandateRepository.findByIdAndUser(mandateId, user);

		List<Fund> funds = new ArrayList<>();
		fundRepository.findAll().forEach(funds::add);

		List<MandateContentFile> files = mandateContentCreator.getContentFiles(user, mandate, funds);

		byte[] pdfContent = files.get(0).getContent();//TODO
		MobileIdSignatureFile file = new MobileIdSignatureFile("mandate.pdf", "application/pdf", pdfContent);
		return signService.startSign(file, user.getPersonalCode(), user.getPhoneNumber());
	}

	public String getSignatureStatus(MandateSignatureSession session) {
		MobileIdSignatureSession mobileIdSignatureSession = new MobileIdSignatureSession(session.getSessCode());
		byte[] signedFile = signService.getSignedFile(mobileIdSignatureSession);
		return signedFile == null ? "OUTSTANDING_TRANSACTION" : "SIGNATURE";
	}
}
