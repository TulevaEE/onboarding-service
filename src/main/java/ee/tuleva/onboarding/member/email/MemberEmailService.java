package ee.tuleva.onboarding.member.email;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class MemberEmailService {
    private final EmailService emailService;
    private final MemberEmailContentService emailContentService;

    @Autowired
    public MemberEmailService(EmailService emailService,
                               MemberEmailContentService emailContentService) {
        this.emailService = emailService;
        this.emailContentService = emailContentService;
    }

    public void sendMemberNumber(User user) {
        log.info("Sending member number email to user: {}", user);
        MandrillMessage message = emailService.newMandrillMessage(
            emailService.getRecipients(user), getMemberNumberEmailSubject(),
            emailContentService.getMembershipEmailHtml(user),
            getMemberNumberTags(), null);

        if(message == null) {
            log.warn(
                "Failed to create mandrill message, not sending member number email for userId {}, member #",
                user.getId(),
                user.getMemberOrThrow().getMemberNumber()
            );
            return;
        }

        emailService.send(user, message);
    }

    private String getMemberNumberEmailSubject() {
        return "Tuleva liikmetunnistus";
    }

    private List<String> getMemberNumberTags() {
        return Arrays.asList("memberNumber");
    }
}
