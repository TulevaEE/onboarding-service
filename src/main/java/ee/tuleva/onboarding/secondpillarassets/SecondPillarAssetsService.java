package ee.tuleva.onboarding.secondpillarassets;

import ee.tuleva.onboarding.account.CashFlowService;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.cashflows.CashFlow;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecondPillarAssetsService {

  private final EpisService episService;
  private final CashFlowService cashFlowService;

  public SecondPillarAssets getSecondPillarAssets(Person person) {
    SecondPillarAssets assets = episService.getSecondPillarAssets(person);
    return withTransferredToPik(assets, transferredToPik(person, assets));
  }

  private BigDecimal transferredToPik(Person person, SecondPillarAssets assets) {
    if (!assets.pikFlag()) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    CashFlowStatement statement = cashFlowService.getCashFlowStatement(person);
    BigDecimal netOut =
        statement.getTransactions().stream()
            .map(SecondPillarAssetsService::signedPikFlow)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return netOut.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
  }

  private static BigDecimal signedPikFlow(CashFlow cashFlow) {
    if (cashFlow.isTransferToPik()) {
      return cashFlow.getAmount().abs();
    }
    if (cashFlow.isTransferFromPik()) {
      return cashFlow.getAmount().abs().negate();
    }
    return BigDecimal.ZERO;
  }

  private static SecondPillarAssets withTransferredToPik(
      SecondPillarAssets assets, BigDecimal transferredToPik) {
    return new SecondPillarAssets(
        assets.pikFlag(),
        assets.balance(),
        assets.employeeWithheldPortion(),
        assets.socialTaxPortion(),
        assets.additionalParentalBenefit(),
        assets.interest(),
        assets.compensation(),
        assets.insurance(),
        assets.corrections(),
        assets.inheritance(),
        assets.withdrawals(),
        transferredToPik);
  }
}
