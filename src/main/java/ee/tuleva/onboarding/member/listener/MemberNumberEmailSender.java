package ee.tuleva.onboarding.member.listener;

import ee.tuleva.onboarding.member.email.MemberEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberNumberEmailSender {
  private final MemberEmailService emailService;

  @Async
  @EventListener
  public void onMemberCreatedEvent(MemberCreatedEvent event) {
    emailService.sendMemberNumber(event.getUser(), event.getLocale());
  }
}
