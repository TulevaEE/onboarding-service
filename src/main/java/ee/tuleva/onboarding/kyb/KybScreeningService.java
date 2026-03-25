package ee.tuleva.onboarding.kyb;

import ee.tuleva.onboarding.kyb.screener.*;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KybScreeningService {

  private final List<KybScreener> screeners;
  private final KybDataChangeDetector dataChangeDetector;
  private final ApplicationEventPublisher eventPublisher;

  public List<KybCheck> screen(KybCompanyData companyData) {
    var screenerResults =
        screeners.stream().map(s -> s.screen(companyData)).flatMap(Collection::stream).toList();

    var dataChangedCheck = dataChangeDetector.detect(companyData.personalCode(), screenerResults);

    var results = Stream.concat(screenerResults.stream(), Stream.of(dataChangedCheck)).toList();

    eventPublisher.publishEvent(
        new KybCheckPerformedEvent(this, companyData.personalCode(), results));

    return results;
  }
}
