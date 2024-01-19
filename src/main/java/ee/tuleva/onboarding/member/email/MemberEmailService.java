package ee.tuleva.onboarding.member.email;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.member.Member;
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
  private final EmailPersistenceService emailPersistenceService;

  public void sendMemberNumber(User user, Locale locale) {
    log.info("Sending member number email to user: {}", user.getId());
    Member member = user.getMemberOrThrow();
    EmailType emailType = EmailType.from(member);
    String templateName = emailType.getTemplateName(locale);

    MandrillMessage message =
        emailService.newMandrillMessage(
            user.getEmail(),
            emailType.getTemplateName(locale),
            Map.of(
                "fname", user.getFirstName(),
                "lname", user.getLastName(),
                "memberNumber", member.getMemberNumber(),
                "memberDate", dateFormatter().format(member.getCreatedDate())),
            List.of("memberNumber"),
            null);

    if (message == null) {
      log.warn(
          "Failed to create mandrill message, not sending member number email for userId {}, member #{}",
          user.getId(),
          member.getMemberNumber());
      return;
    }

    emailService
        .send(user, message, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    user, response.getId(), emailType, response.getStatus()));
  }

  private DateTimeFormatter dateFormatter() {
    return DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Tallinn"));
  }
}
