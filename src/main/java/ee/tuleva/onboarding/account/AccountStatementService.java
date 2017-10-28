package ee.tuleva.onboarding.account;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.account.FundBalanceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static java.util.stream.Collectors.toList;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountStatementService {

  private final EpisService episService;
  private final FundBalanceDtoToFundBalanceConverter fundBalanceConverter;

  public List<FundBalance> getAccountStatement(Person person) {
    List<FundBalanceDto> accountStatement = episService.getAccountStatement(person);

    return accountStatement.stream()
        .map(fundBalanceConverter::convert)
        .collect(toList());
  }
}
