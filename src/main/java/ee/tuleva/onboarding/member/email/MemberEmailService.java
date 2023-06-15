package ee.tuleva.onboarding.member.email;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemberEmailService {
  private final EmailService emailService;

  public void sendMemberNumber(User user, Locale locale) {
    log.info("Sending member number email to user: {}", user.getId());
    String templateName = "member_" + locale.getLanguage();

    MandrillMessage message =
        emailService.newMandrillMessage(
            user.getEmail(),
            templateName,
            Map.of(
                "fname", user.getFirstName(),
                "lname", user.getLastName(),
                "memberNumber", user.getMemberOrThrow().getMemberNumber(),
                "memberDate", dateFormatter().format(user.getMemberOrThrow().getCreatedDate())),
            List.of("memberNumber"),
            null);

    if (message == null) {
      log.warn(
          "Failed to create mandrill message, not sending member number email for userId {}, member #{}",
          user.getId(),
          user.getMemberOrThrow().getMemberNumber());
      return;
    }

    emailService.send(user, message, templateName);
  }

  private DateTimeFormatter dateFormatter() {
    return DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Tallinn"));
  }
}
