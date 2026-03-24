package ee.tuleva.onboarding.kyb;

import ee.tuleva.onboarding.kyb.screener.*;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KybScreeningService {

  private final List<KybScreener> screeners;
  private final ApplicationEventPublisher eventPublisher;

  public List<KybCheck> screen(KybCompanyData companyData) {
    var results =
        screeners.stream().map(s -> s.screen(companyData)).flatMap(Collection::stream).toList();

    eventPublisher.publishEvent(
        new KybCheckPerformedEvent(this, companyData.personalCode(), results));

    return results;
  }
}
