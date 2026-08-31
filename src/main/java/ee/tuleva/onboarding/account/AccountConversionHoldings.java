package ee.tuleva.onboarding.account;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.conversion.ConversionHolding;
import ee.tuleva.onboarding.conversion.ConversionHoldings;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AccountConversionHoldings implements ConversionHoldings {

  private final AccountStatementService accountStatementService;

  @Override
  public List<ConversionHolding> forPerson(Person person) {
    return accountStatementService.getAccountStatement(person).stream()
        .map(AccountConversionHoldings::toConversionHolding)
        .toList();
  }

  private static ConversionHolding toConversionHolding(FundBalance fundBalance) {
    return new ConversionHolding(
        fundBalance.getPillar(),
        fundBalance.getIsin(),
        fundBalance.isOwnFund(),
        fundBalance.isExitRestricted(),
        fundBalance.isActiveContributions(),
        fundBalance.getTotalValue(),
        fundBalance.getTotalUnits(),
        fundBalance.getFee());
  }
}
