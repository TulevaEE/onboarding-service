package ee.tuleva.onboarding.kyb;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.kyb.screener.*;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
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
  private final KybCheckOverrideRepository kybCheckOverrideRepository;
  private final Clock clock;

  public List<KybCheck> validate(KybCompanyData companyData) {
    var results =
        screeners.stream().map(s -> s.screen(companyData)).flatMap(Collection::stream).toList();
    return applyOverrides(companyData.company().registryCode().value(), results);
  }

  private List<KybCheck> applyOverrides(String registryCode, List<KybCheck> results) {
    var overrides = kybCheckOverrideRepository.findByRegistryCode(registryCode);
    if (overrides.isEmpty()) {
      return results;
    }
    var overrideByType =
        overrides.stream()
            .filter(override -> override.getExpiresAt().isAfter(clock.instant()))
            .collect(toMap(KybCheckOverride::getCheckType, identity()));
    return results.stream()
        .map(check -> applyOverride(check, overrideByType.get(check.type())))
        .toList();
  }

  private KybCheck applyOverride(KybCheck check, KybCheckOverride override) {
    if (override == null) {
      return check;
    }
    var metadata = new LinkedHashMap<>(check.metadata());
    metadata.put("overridden", true);
    metadata.put("overrideReason", override.getReason());
    return new KybCheck(check.type(), override.isForcedSuccess(), metadata);
  }

  public List<KybCheck> screen(KybCompanyData companyData) {
    var screenerResults = validate(companyData);

    var dataChangedCheck =
        dataChangeDetector.detect(
            companyData.personalCode(), companyData.company().registryCode(), screenerResults);

    var results = Stream.concat(screenerResults.stream(), Stream.of(dataChangedCheck)).toList();

    eventPublisher.publishEvent(
        new KybCheckPerformedEvent(
            this,
            companyData.company(),
            companyData.personalCode(),
            companyData.relatedPersons(),
            results,
            companyData.representationRights()));

    return results;
  }
}
