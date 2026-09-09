package ee.tuleva.onboarding.member.email;

import static ee.tuleva.onboarding.mandate.EmailVariablesAttachments.getNameMergeVars;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.notification.email.EmailType;
import ee.tuleva.onboarding.nudge.NudgeContext;
import ee.tuleva.onboarding.nudge.NudgeDecision;
import ee.tuleva.onboarding.nudge.NudgeDecisionService;
import ee.tuleva.onboarding.nudge.NudgeKey;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.member.Member;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
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
  private final NudgeDecisionService nudgeDecisionService;

  public void sendMemberNumber(User user, Locale locale) {
    log.info("Sending member number email to user: {}", user.getId());
    Member member = user.getMemberOrThrow();
    EmailType emailType = EmailType.MEMBERSHIP;
    String templateName = emailType.getTemplateName(locale);
    NudgeDecision decision = nudgeFor(user);

    MandrillMessage message =
        emailService.newMandrillMessage(
            user.getEmail(),
            templateName,
            getMergeVars(user, member, decision, locale),
            List.of("memberNumber", decision.tag()));

    emailService
        .send(user, message, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    user, response.getId(), emailType, response.getStatus(), decision.tag()));
  }

  private NudgeDecision nudgeFor(User user) {
    try {
      return nudgeDecisionService.decide(user, NudgeContext.MEMBERSHIP);
    } catch (RuntimeException e) {
      log.warn("Sending the membership email without a nudge: userId={}", user.getId(), e);
      return NudgeDecision.of(NudgeKey.NONE);
    }
  }

  private Map<String, Object> getMergeVars(
      User user, Member member, NudgeDecision decision, Locale locale) {
    Map<String, Object> variables =
        new HashMap<>(
            Map.of(
                "memberNumber", member.getMemberNumber(),
                "memberDate", dateFormatter().format(member.getCreatedDate())));
    variables.putAll(getNameMergeVars(user));
    variables.putAll(decision.mergeVars(locale));
    return variables;
  }

  private DateTimeFormatter dateFormatter() {
    return DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Tallinn"));
  }
}
