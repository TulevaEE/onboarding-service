package ee.tuleva.onboarding.account;

import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.conversion.ConversionCashFlow;
import ee.tuleva.onboarding.conversion.ConversionCashFlows;
import ee.tuleva.onboarding.epis.CashFlow;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
class AccountConversionCashFlows implements ConversionCashFlows {

  private final CashFlowService cashFlowService;
  private final FundRepository fundRepository;

  @Override
  public List<ConversionCashFlow> forPerson(Person person) {
    return cashFlowService.getCashFlowStatement(person).getTransactions().stream()
        .filter(cashFlow -> cashFlow.isContribution() || cashFlow.isSubtraction())
        .flatMap(cashFlow -> toConversionCashFlow(cashFlow, fundRepository).stream())
        .toList();
  }

  private static Optional<ConversionCashFlow> toConversionCashFlow(
      CashFlow cashFlow, FundRepository fundRepository) {
    return pillarOf(cashFlow, fundRepository)
        .map(
            pillar ->
                new ConversionCashFlow(
                    pillar,
                    cashFlow.getAmount(),
                    cashFlow.getPriceTime(),
                    cashFlow.isCashContribution(),
                    cashFlow.isContribution(),
                    cashFlow.isSubtraction()));
  }

  private static Optional<Integer> pillarOf(CashFlow cashFlow, FundRepository fundRepository) {
    String isin = requireNonNull(cashFlow.getIsin(), "Cash flow missing isin");
    Fund fund = fundRepository.findByIsin(isin);
    if (fund == null) {
      if (cashFlow.isCashContribution()) {
        throw new IllegalStateException("Cash contribution fund not found: isin=" + isin);
      }
      log.warn("Cash flow fund not found: isin={}", isin);
      return Optional.empty();
    }
    return Optional.of(fund.getPillar());
  }
}
