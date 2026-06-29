package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.RELATED_PERSONS_KYC;
import static ee.tuleva.onboarding.kyb.KybKycStatus.COMPLETED;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import ee.tuleva.onboarding.kyb.RelatedPersonsKycMetadata;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RelatedPersonsKycScreener implements KybScreener {

  @Override
  public List<KybCheck> screen(KybCompanyData companyData) {
    var incomplete =
        companyData.relatedPersons().stream().filter(p -> p.kycStatus() != COMPLETED).toList();

    return List.of(
        new KybCheck(
            RELATED_PERSONS_KYC,
            incomplete.isEmpty(),
            RelatedPersonsKycMetadata.forIncompletePersons(incomplete)));
  }
}
