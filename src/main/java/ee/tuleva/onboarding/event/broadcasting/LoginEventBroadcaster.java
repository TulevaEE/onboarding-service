package ee.tuleva.onboarding.event.broadcasting;

import static ee.tuleva.onboarding.event.TrackableEventType.LOGIN;

import ee.tuleva.onboarding.auth.SecurityContextRunner;
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.ConversionDecorator;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.ContactDetailsService;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LoginEventBroadcaster {

  private final ApplicationEventPublisher eventPublisher;
  private final UserConversionService conversionService;
  private final ContactDetailsService contactDetailsService;
  private final ConversionDecorator conversionDecorator;
  private final SecurityContextRunner securityContextRunner;
  private final SecondPillarPaymentRateService secondPillarPaymentRateService;

  @EventListener
  public void onAfterTokenGrantedEvent(AfterTokenGrantedEvent event) {
    AuthenticatedPerson person = event.getPerson();
    Map<String, @Nullable Object> data = new HashMap<>(person.getAttributes());

    data.put("method", event.getGrantType());
    if (event.isIdCard()) {
      data.put("document", event.getIdDocumentType());
    }

    securityContextRunner.runAs(
        person,
        event.getAccessToken(),
        () -> {
          var conversion = conversionService.getConversion(person);
          var contactDetails = contactDetailsService.getContactDetails(person);
          var paymentRates = secondPillarPaymentRateService.getPaymentRates(person);
          conversionDecorator.addConversionMetadata(
              data, conversion, contactDetails, person, paymentRates);

          eventPublisher.publishEvent(new TrackableEvent(person, LOGIN, data));
        });
  }
}
