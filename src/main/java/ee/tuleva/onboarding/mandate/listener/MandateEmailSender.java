package ee.tuleva.onboarding.mandate.listener;

import ee.tuleva.onboarding.mandate.email.MandateEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MandateEmailSender {
    private final MandateEmailService emailService;

    @Async
    @EventListener
    public void onSecondPillarMandateCreatedEvent(SecondPillarMandateCreatedEvent event) {
        emailService.sendSecondPillarMandate(event.getUser(), event.getMandateId(), event.getSignedFile());
    }

    @Async
    @EventListener
    public void onThirdPillarMandateCreatedEvent(ThirdPillarMandateCreatedEvent event) {
        emailService.sendThirdPillarMandate(
            event.getUser(),
            event.getMandateId(),
            event.getSignedFile(),
            event.getPensionAccountNumber()
        );
    }

}
