package ee.tuleva.onboarding.event.broadcasting;

import static ee.tuleva.onboarding.auth.principal.AuthenticatedPerson.SMART_ID_DOCUMENT_NUMBER;
import static ee.tuleva.onboarding.event.TrackableEvent.IP_ADDRESS;
import static ee.tuleva.onboarding.event.TrackableEvent.USER_AGENT;
import static ee.tuleva.onboarding.event.TrackableEventType.LOGIN;

import ee.tuleva.onboarding.auth.ClientConnection;
import ee.tuleva.onboarding.auth.SecurityContextRunner;
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.ConversionDecorator;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.event.PillarActivations;
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
  private final PillarActivations pillarActivations;
  private final ConversionDecorator conversionDecorator;
  private final SecurityContextRunner securityContextRunner;
  private final SecondPillarPaymentRateService secondPillarPaymentRateService;
  private final ClientConnection clientConnection;

  @EventListener
  public void onAfterTokenGrantedEvent(AfterTokenGrantedEvent event) {
    AuthenticatedPerson person = event.getPerson();
    Map<String, @Nullable Object> data = new HashMap<>(person.getAttributes());
    data.remove(SMART_ID_DOCUMENT_NUMBER);

    data.put("method", event.getGrantType());
    if (event.isIdCard()) {
      data.put("document", event.getIdDocumentType());
    }
    data.putAll(connectionReadOnTheRequestThread());

    securityContextRunner.runAs(
        person,
        event.getAccessToken(),
        () -> {
          var conversion = conversionService.getConversion(person);
          var pillarActivation = pillarActivations.forPerson(person);
          var paymentRates = secondPillarPaymentRateService.getPaymentRates(person);
          conversionDecorator.addConversionMetadata(
              data,
              conversion,
              pillarActivation.secondPillarActive(),
              pillarActivation.thirdPillarActive(),
              person,
              paymentRates);

          eventPublisher.publishEvent(new TrackableEvent(person, LOGIN, data));
        });
  }

  private Map<String, Object> connectionReadOnTheRequestThread() {
    Map<String, Object> connection = new HashMap<>();
    clientConnection.ipAddress().ifPresent(ipAddress -> connection.put(IP_ADDRESS, ipAddress));
    clientConnection.userAgent().ifPresent(userAgent -> connection.put(USER_AGENT, userAgent));
    return connection;
  }
}
