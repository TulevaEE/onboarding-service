package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.ALL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.INSTRUMENT_RATE_COVERAGE;
import static java.util.function.Predicate.not;

import ee.tuleva.onboarding.investment.fees.rate.InstrumentOcfService;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavAccountLine;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class InstrumentRateCoverageChecker {

  private final InstrumentOcfService instrumentOcfService;
  private final FundNavQueryService fundNavQueryService;
  private final ModelPortfolioAllocationRepository allocationRepository;

  List<FeeCheckFinding> check(TulevaFund fund, LocalDate checkDate) {
    var held = heldIsins(fund, checkDate);
    if (held.isEmpty()) {
      return List.of(
          finding(
              fund,
              FeeCheckSeverity.NOT_RUN,
              List.of(),
              "No published NAV on or before "
                  + checkDate
                  + " to read the held instruments from, so their fee agreements could not be"
                  + " checked"));
    }
    var covered = instrumentOcfService.isinsWithAnAgreementOn(checkDate);
    var heldWithoutAnAgreement = withoutAnAgreement(held.get(), covered);
    var modelledWithoutAnAgreement =
        withoutAnAgreement(modelledIsins(fund, checkDate), covered).stream()
            .filter(not(heldWithoutAnAgreement::contains))
            .toList();
    var findings = new ArrayList<FeeCheckFinding>();
    if (!heldWithoutAnAgreement.isEmpty()) {
      findings.add(
          finding(
              fund,
              FeeCheckSeverity.FAIL,
              heldWithoutAnAgreement,
              "Held instruments have no fee agreement in investment_instrument_fee, so the"
                  + " month's OCF cannot be calculated: "
                  + heldWithoutAnAgreement));
    }
    if (!modelledWithoutAnAgreement.isEmpty()) {
      findings.add(
          finding(
              fund,
              FeeCheckSeverity.WARNING,
              modelledWithoutAnAgreement,
              "Model portfolio instruments have no fee agreement in investment_instrument_fee"
                  + " yet: "
                  + modelledWithoutAnAgreement));
    }
    return findings.isEmpty()
        ? List.of(FeeCheckFinding.pass(fund, INSTRUMENT_RATE_COVERAGE, ALL))
        : findings;
  }

  private Optional<List<String>> heldIsins(TulevaFund fund, LocalDate checkDate) {
    return fundNavQueryService
        .findLatestPublishedNavDateOnOrBefore(fund.getCode(), checkDate)
        .flatMap(navDate -> fundNavQueryService.findPublishedCalculation(fund.getCode(), navDate))
        .map(
            calculation ->
                calculation.securityLines().stream()
                    .map(NavAccountLine::accountId)
                    .filter(Objects::nonNull)
                    .toList());
  }

  private List<String> modelledIsins(TulevaFund fund, LocalDate checkDate) {
    return allocationRepository.findLatestByFundAsOf(fund, checkDate).stream()
        .map(ModelPortfolioAllocation::getIsin)
        .filter(Objects::nonNull)
        .toList();
  }

  private static List<String> withoutAnAgreement(List<String> isins, Set<String> covered) {
    return isins.stream().filter(not(covered::contains)).distinct().sorted().toList();
  }

  private static FeeCheckFinding finding(
      TulevaFund fund, FeeCheckSeverity severity, List<String> isins, String message) {
    return new FeeCheckFinding(
        fund,
        INSTRUMENT_RATE_COVERAGE,
        ALL,
        severity,
        message,
        null,
        isins,
        Map.of("isins", isins));
  }
}
