package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.RELATED_PERSONS_KYC;
import static ee.tuleva.onboarding.kyb.KybKycStatus.COMPLETED;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import ee.tuleva.onboarding.kyb.KybRelatedPerson;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RelatedPersonsKycScreener implements KybScreener {

  @Override
  public Optional<KybCheck> screen(KybCompanyData companyData) {
    var incomplete =
        companyData.relatedPersons().stream().filter(p -> p.kycStatus() != COMPLETED).toList();

    return Optional.of(
        new KybCheck(RELATED_PERSONS_KYC, incomplete.isEmpty(), buildMetadata(incomplete)));
  }

  private Map<String, Object> buildMetadata(List<KybRelatedPerson> incomplete) {
    if (incomplete.isEmpty()) {
      return Map.of();
    }
    var incompletePersons =
        incomplete.stream()
            .map(
                p ->
                    Map.of(
                        "personalCode", p.personalCode(),
                        "kycStatus", p.kycStatus().name()))
            .toList();
    return Map.of("incompletePersons", incompletePersons);
  }
}
