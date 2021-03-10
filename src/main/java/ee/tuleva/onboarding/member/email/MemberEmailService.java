package ee.tuleva.onboarding.member.email;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemberEmailService {
  private final EmailService emailService;
  private final MemberEmailContentService emailContentService;

  public void sendMemberNumber(User user, Locale locale) {
    log.info("Sending member number email to user: {}", user);
    MandrillMessage message =
        emailService.newMandrillMessage(
            emailService.getRecipients(user),
            getMemberNumberEmailSubject(),
            emailContentService.getMembershipEmailHtml(user, locale),
            getMemberNumberTags(),
            null);

    if (message == null) {
      log.warn(
          "Failed to create mandrill message, not sending member number email for userId {}, member #",
          user.getId(),
          user.getMemberOrThrow().getMemberNumber());
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
