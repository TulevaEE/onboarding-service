package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.KYC_CHECK;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AmlKycChecks implements KycChecks {

  private final AmlCheckRepository amlCheckRepository;

  @Override
  public Optional<Boolean> latestKycCheckPassedWithinLastYear(String personalCode) {
    return amlCheckRepository
        .findFirstByPersonalCodeAndTypeAndCreatedTimeAfterOrderByCreatedTimeDescIdDesc(
            personalCode, KYC_CHECK, aYearAgo())
        .map(AmlCheck::isSuccess);
  }
}
