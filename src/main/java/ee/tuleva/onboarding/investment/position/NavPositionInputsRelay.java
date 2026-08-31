package ee.tuleva.onboarding.investment.position;

import ee.tuleva.onboarding.investment.event.FundPositionsImported;
import ee.tuleva.onboarding.savings.fund.nav.NavPositionInputsImported;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NavPositionInputsRelay {

  private final ApplicationEventPublisher eventPublisher;

  @EventListener(classes = FundPositionsImported.class)
  void onFundPositionsImported() {
    eventPublisher.publishEvent(new NavPositionInputsImported());
  }
}
