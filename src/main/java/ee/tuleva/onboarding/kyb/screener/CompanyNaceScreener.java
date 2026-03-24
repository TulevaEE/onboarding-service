package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.HIGH_RISK_NACE;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CompanyNaceScreener implements KybScreener {

  static final Set<String> HIGH_RISK_NACE_CODES =
      Set.of(
          "64321", "66191", "66199", "66301", "69202", "92001", "92009", "46481", "46722", "47771",
          "47791");

  @Override
  public List<KybCheck> screen(KybCompanyData companyData) {
    var naceCode = companyData.company().naceCode();
    boolean isHighRisk = naceCode == null || HIGH_RISK_NACE_CODES.contains(naceCode);
    return List.of(
        new KybCheck(
            HIGH_RISK_NACE,
            !isHighRisk,
            Map.of("naceCode", naceCode != null ? naceCode : "unknown")));
  }
}
