package ee.tuleva.onboarding.mandate;

import com.codeborne.security.mobileid.MobileIdSignatureFile;
import com.codeborne.security.mobileid.MobileIdSignatureSession;
import ee.tuleva.onboarding.sign.MobileIdSignService;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class MandateService {

    private final MandateRepository mandateRepository;
	private final MobileIdSignService signService;

    CreateMandateCommandToMandateConverter converter = new CreateMandateCommandToMandateConverter();

    public Mandate save(User user, CreateMandateCommand createMandateCommand) {

        Mandate mandate = converter.convert(createMandateCommand);
        mandate.setUser(user);

        return mandateRepository.save(mandate);
    }

	public MobileIdSignatureSession sign(Long mandateId, User user) {
		Mandate mandate = mandateRepository.findByIdAndUser(mandateId, user);
		byte[] pdfContent = "todo".getBytes();
		MobileIdSignatureFile file = new MobileIdSignatureFile("mandate.pdf", "application/pdf", pdfContent);
		return signService.startSign(file, user.getPersonalCode(), user.getPhoneNumber());
	}

	public String getSignatureStatus(MandateSignatureSession session) {
		MobileIdSignatureSession mobileIdSignatureSession = new MobileIdSignatureSession(session.getSessCode());
		byte[] signedFile = signService.getSignedFile(mobileIdSignatureSession);
		return signedFile == null ? "OUTSTANDING_TRANSACTION" : "SIGNATURE";
	}
}
