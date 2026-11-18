package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.aml.ScreeningOutcome.CLEAR;
import static ee.tuleva.onboarding.aml.ScreeningOutcome.PEP_HIT;
import static ee.tuleva.onboarding.aml.ScreeningOutcome.SANCTION_HIT;
import static ee.tuleva.onboarding.aml.ScreeningOutcome.UNAVAILABLE;
import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_PEP;
import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_SANCTION;

import ee.tuleva.onboarding.aml.ScreeningOutcome;
import ee.tuleva.onboarding.aml.sanctions.ScreenedCompany;
import ee.tuleva.onboarding.kyb.screener.CompanySanctionScreener;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanySanctionCheck {

  private final CompanySanctionScreener companySanctionScreener;

  public ScreeningOutcome screeningOutcome(String registryCode, @Nullable String name) {
    if (name == null || name.isBlank()) {
      log.warn("Company without a name cannot be sanction screened: registryCode={}", registryCode);
      return UNAVAILABLE;
    }
    try {
      return outcome(companySanctionScreener.screen(new ScreenedCompany(name, registryCode)));
    } catch (RuntimeException e) {
      log.error("Company sanction screening failed: registryCode={}", registryCode, e);
      return UNAVAILABLE;
    }
  }

  private static ScreeningOutcome outcome(List<KybCheck> checks) {
    if (!passed(checks, COMPANY_SANCTION)) {
      return SANCTION_HIT;
    }
    return passed(checks, COMPANY_PEP) ? CLEAR : PEP_HIT;
  }

  private static boolean passed(List<KybCheck> checks, KybCheckType type) {
    return checks.stream()
        .filter(check -> check.type() == type)
        .findFirst()
        .map(KybCheck::success)
        .orElseThrow(() -> new IllegalStateException("Missing company check: type=" + type));
  }
}
