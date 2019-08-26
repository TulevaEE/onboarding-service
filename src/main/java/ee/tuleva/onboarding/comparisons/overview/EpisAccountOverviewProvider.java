package ee.tuleva.onboarding.comparisons.overview;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
import ee.tuleva.onboarding.epis.cashflows.CashFlow;
import ee.tuleva.onboarding.epis.fund.FundDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
    public AccountOverview getAccountOverview(Person person, Instant startTime, Integer pillar) {
        Instant endTime = Instant.now();
        CashFlowStatement cashFlowStatement =
            episService.getCashFlowStatement(person, toLocalDate(startTime), toLocalDate(endTime));
        return transformCashFlowStatementToAccountOverview(cashFlowStatement, startTime, endTime, pillar);
    }

    private AccountOverview transformCashFlowStatementToAccountOverview(
        CashFlowStatement cashFlowStatement, Instant startTime, Instant endTime, Integer pillar) {
        BigDecimal beginningBalance = convertBalance(cashFlowStatement.getStartBalance(), pillar);
        BigDecimal endingBalance = convertBalance(cashFlowStatement.getEndBalance(), pillar);
        List<Transaction> transactions = convertTransactions(cashFlowStatement.getTransactions(), pillar);

        return AccountOverview.builder()
            .beginningBalance(beginningBalance)
            .endingBalance(endingBalance)
            .transactions(transactions)
            .startTime(startTime)
            .endTime(endTime)
            .pillar(pillar)
            .build();
    }

    private BigDecimal convertBalance(Map<String, CashFlow> balance, Integer pillar) {
        return balance.values().stream()
            .filter(cashFlow -> getAllIsinsBy(pillar).contains(cashFlow.getIsin()))
            .map(cashFlow -> convertCurrencyToEur(cashFlow.getAmount(), cashFlow.getCurrency()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<Transaction> convertTransactions(List<CashFlow> cashFlowValues, Integer pillar) {
        return cashFlowValues.stream()
            .filter(cashFlow -> getAllIsinsBy(pillar).contains(cashFlow.getIsin()))
            .map(this::convertTransaction)
            .collect(toList());
    }

    @NotNull
    private List<String> getAllIsinsBy(Integer pillar) {
        return episService.getFunds().stream()
            .filter(fund -> fund.getPillar() == pillar)
            .map(FundDto::getIsin)
            .collect(toList());
    }

    private Transaction convertTransaction(CashFlow cashFlowValue) {
        BigDecimal amount = convertCurrencyToEur(cashFlowValue.getAmount().negate(), cashFlowValue.getCurrency());
        return new Transaction(amount, cashFlowValue.getDate());
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

    private LocalDate toLocalDate(Instant startTime) {
        return startTime == null ? null : LocalDateTime.ofInstant(startTime, ZoneOffset.UTC).toLocalDate();
    }
}
