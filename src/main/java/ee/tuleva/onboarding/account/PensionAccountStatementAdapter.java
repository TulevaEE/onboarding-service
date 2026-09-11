package ee.tuleva.onboarding.account;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.mandate.PensionAccountStatement;
import ee.tuleva.onboarding.mandate.PensionAccountStatement.PensionFundBalance;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PensionAccountStatementAdapter implements PensionAccountStatement {

  private final AccountStatementService accountStatementService;

  @Override
  public List<PensionFundBalance> forPerson(Person person) {
    return accountStatementService.getAccountStatement(person).stream()
        .map(
            fundBalance ->
                new PensionFundBalance(
                    fundBalance.getIsin(),
                    fundBalance.getUnits(),
                    fundBalance.isActiveContributions()))
        .toList();
  }
}
