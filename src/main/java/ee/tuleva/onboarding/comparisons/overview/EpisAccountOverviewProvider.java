package ee.tuleva.onboarding.comparisons.overview;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatementDto;
import ee.tuleva.onboarding.epis.cashflows.CashFlowValueDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

@Service
@Slf4j
@RequiredArgsConstructor
public class EpisAccountOverviewProvider implements AccountOverviewProvider {

    private static final BigDecimal EEK_TO_EUR_EXCHANGE_RATE = new BigDecimal("15.6466");

    private final EpisService episService;

    @Override
    public AccountOverview getAccountOverview(Person person, Instant startTime) {
        Instant endTime = Instant.now();
        CashFlowStatementDto cashFlowStatement = episService.getCashFlowStatement(person, startTime, endTime);
        return transformCashFlowStatementToAccountOverview(cashFlowStatement, startTime, endTime);
    }

    private AccountOverview transformCashFlowStatementToAccountOverview(CashFlowStatementDto cashFlowStatementDto, Instant startTime, Instant endTime) {
        BigDecimal beginningBalance = convertBalance(cashFlowStatementDto.getStartBalance());
        BigDecimal endingBalance = convertBalance(cashFlowStatementDto.getEndBalance());
        List<Transaction> transactions = convertTransactions(cashFlowStatementDto.getTransactions());

        return AccountOverview.builder()
                .beginningBalance(beginningBalance)
                .endingBalance(endingBalance)
                .transactions(transactions)
                .startTime(startTime)
                .endTime(endTime)
                .build();
    }

    private BigDecimal convertBalance(Map<String, CashFlowValueDto> balance) {
        return balance.values().stream()
                .filter(cashFlowValueDto -> cashFlowValueDto.getPillar() == 2)
                .map(cashFlowValueDto -> convertCurrencyToEur(cashFlowValueDto.getAmount(), cashFlowValueDto.getCurrency()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<Transaction> convertTransactions(List<CashFlowValueDto> cashFlowValues) {
        return cashFlowValues.stream()
                .filter(cashFlowValueDto -> cashFlowValueDto.getPillar() == 2)
                .map(this::convertTransaction)
                .collect(toList());
    }

    private Transaction convertTransaction(CashFlowValueDto cashFlowValue) {
        BigDecimal amount = convertCurrencyToEur(cashFlowValue.getAmount().negate(), cashFlowValue.getCurrency());
        return new Transaction(amount, cashFlowValue.getTime());
    }

    private BigDecimal convertCurrencyToEur(BigDecimal amount, String currency) {
        if ("EUR".equalsIgnoreCase(currency)) {
            return amount;
        } else if ("EEK".equalsIgnoreCase(currency)) {
            return amount.divide(EEK_TO_EUR_EXCHANGE_RATE, MathContext.DECIMAL128);
        } else {
            log.error("Needed to convert currency other than EEK, statement will be invalid");
            return amount;
        }
    }
}
