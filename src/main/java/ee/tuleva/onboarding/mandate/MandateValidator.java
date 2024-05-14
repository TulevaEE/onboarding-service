package ee.tuleva.onboarding.mandate;

import ee.tuleva.onboarding.account.AccountStatementService;
import ee.tuleva.onboarding.account.FundBalance;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand;
import ee.tuleva.onboarding.mandate.exception.InvalidMandateException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MandateValidator {

  private final AccountStatementService accountStatementService;
  private final FundRepository fundRepository;

  public void validate(CreateMandateCommand createMandateCommand, Person person) {
    if (countValuesBiggerThanOne(summariseSourceFundTransferAmounts(createMandateCommand)) > 0) {
      throw InvalidMandateException.sourceAmountExceeded();
    }
    if (isSameSourceToTargetTransferPresent(createMandateCommand)) {
      throw InvalidMandateException.sameSourceAndTargetTransferPresent();
    }
    if (isFutureContributionsToSameFund(createMandateCommand, person)) {
      throw InvalidMandateException.futureContributionsToSameFund();
    }
  }

  private Map<String, BigDecimal> summariseSourceFundTransferAmounts(
      CreateMandateCommand createMandateCommand) {
    Map<String, BigDecimal> summaryMap = new HashMap<>();

    createMandateCommand
        .getFundTransferExchanges()
        .forEach(
            exchange -> {
              if (!summaryMap.containsKey(exchange.getSourceFundIsin())) {
                summaryMap.put(exchange.getSourceFundIsin(), BigDecimal.ZERO);
              }

              summaryMap.put(
                  exchange.getSourceFundIsin(),
                  summaryMap.get(exchange.getSourceFundIsin()).add(exchange.getAmount()));
            });

    return summaryMap;
  }

  private long countValuesBiggerThanOne(Map<String, BigDecimal> summaryMap) {
    return summaryMap.values().stream()
        .filter(value -> value.compareTo(BigDecimal.ONE) > 0)
        .count();
  }

  private boolean isSameSourceToTargetTransferPresent(CreateMandateCommand createMandateCommand) {
    return createMandateCommand.getFundTransferExchanges().stream()
        .anyMatch(
            exchange ->
                exchange.getSourceFundIsin().equalsIgnoreCase(exchange.getTargetFundIsin()));
  }

  private boolean isFutureContributionsToSameFund(
      CreateMandateCommand createMandateCommand, Person person) {
    String isin = createMandateCommand.getFutureContributionFundIsin();
    List<FundBalance> accountStatement = accountStatementService.getAccountStatement(person);
    Fund fund = fundRepository.findByIsin(isin);

    if (isin == null || fund == null) {
      return false;
    }

    if (fund.getPillar() == 2) {
      return accountStatement.stream()
          .anyMatch(
              fundBalance ->
                  fundBalance.isActiveContributions() && fundBalance.getIsin().equals(isin));
    }

    return false;
  }
}
