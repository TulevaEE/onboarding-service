package ee.tuleva.onboarding.event.broadcasting;

import static ee.tuleva.onboarding.event.TrackableEventType.LOGIN;

import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.contact.ContactDetailsService;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LoginEventBroadcaster {

  private final ApplicationEventPublisher eventPublisher;
  private final UserConversionService conversionService;
  private final ContactDetailsService contactDetailsService;
  private final ConversionDecorator conversionDecorator;
  private final GrantedAuthorityFactory grantedAuthorityFactory;
  private final SecondPillarPaymentRateService secondPillarPaymentRateService;

  @EventListener
  public void onAfterTokenGrantedEvent(AfterTokenGrantedEvent event) {
    try {
      AuthenticatedPerson person = event.getPerson();
      Map<String, Object> data = new HashMap<>(person.getAttributes());

      data.put("method", event.getGrantType());
      if (event.isIdCard()) {
        data.put("document", event.getIdDocumentType());
      }

      var authorities = grantedAuthorityFactory.from(person);
      Authentication auth =
          new UsernamePasswordAuthenticationToken(person, event.getAccessToken(), authorities);
      SecurityContextHolder.getContext().setAuthentication(auth);

      var conversion = conversionService.getConversion(person);
      var contactDetails = contactDetailsService.getContactDetails(person);
      var paymentRates = secondPillarPaymentRateService.getPaymentRates(person);
      conversionDecorator.addConversionMetadata(
          data, conversion, contactDetails, person, paymentRates);

      eventPublisher.publishEvent(new TrackableEvent(person, LOGIN, data));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
