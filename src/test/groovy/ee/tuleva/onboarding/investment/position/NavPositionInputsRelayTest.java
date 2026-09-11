package ee.tuleva.onboarding.investment.position;

import static org.mockito.BDDMockito.then;

import ee.tuleva.onboarding.savings.fund.nav.NavPositionInputsImported;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class NavPositionInputsRelayTest {

  @Mock ApplicationEventPublisher eventPublisher;
  @InjectMocks NavPositionInputsRelay relay;

  @Test
  void republishesPositionImportsAsNavInputEvents() {
    relay.onFundPositionsImported();

    then(eventPublisher).should().publishEvent(new NavPositionInputsImported());
  }
}
