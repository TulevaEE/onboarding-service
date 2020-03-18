package ee.tuleva.onboarding.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class BeforeTokenGrantedEventPublisher {

  private final ApplicationEventPublisher applicationEventPublisher;

  public void publish(OAuth2Authentication authentication, GrantType grantType) {
    log.info("Publishing BeforeTokenGrantedEvent. ");
    BeforeTokenGrantedEvent beforeTokenGrantedEvent =
        new BeforeTokenGrantedEvent(this, authentication, grantType);
    applicationEventPublisher.publishEvent(beforeTokenGrantedEvent);
  }
}
