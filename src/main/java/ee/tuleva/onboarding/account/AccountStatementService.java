package ee.tuleva.onboarding.account;

import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.account.FundBalanceDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountStatementService {

  private final EpisService episService;
  private final FundBalanceDtoToFundBalanceConverter fundBalanceConverter;

  public List<FundBalance> getAccountStatement(Person person) {
    List<FundBalanceDto> accountStatement = episService.getAccountStatement(person);

    return accountStatement.stream()
        .filter(fundBalanceDto -> fundBalanceDto.getIsin() != null)
        .map(fundBalanceDto -> convertToFundBalance(fundBalanceDto, person))
        .collect(toList());
  }

  private FundBalance convertToFundBalance(FundBalanceDto fundBalanceDto, Person person) {
    try {
      return fundBalanceConverter.convert(fundBalanceDto, person);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Could not convert fund balance for person " + person, e);
    }
  }
}
