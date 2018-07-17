package ee.tuleva.onboarding.comparisons;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import lombok.RequiredArgsConstructor;
import org.decampo.xirr.Xirr;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FundComparisonCalculatorService {

    private final AccountOverviewProvider accountOverviewProvider;
    private final FundValueProvider estonianAverageValueProvider;
    private final FundValueProvider marketAverageValueProvider;

    private static final int OUTPUT_SCALE = 4;

    public FundComparison calculateComparison(Person person, Instant startTime) {
        AccountOverview overview = accountOverviewProvider.getAccountOverview(person, startTime);
        return calculateForAccountOverview(overview);
    }

    private FundComparison calculateForAccountOverview(AccountOverview accountOverview) {
        double actualRateOfReturn = getRateOfReturn(accountOverview);
        double estonianAverageRateOfReturn = getRateOfReturn(accountOverview, estonianAverageValueProvider);
        double marketAverageRateOfReturn = getRateOfReturn(accountOverview, marketAverageValueProvider);

        return FundComparison.builder()
            .actualReturnPercentage(actualRateOfReturn)
            .estonianAverageReturnPercentage(estonianAverageRateOfReturn)
            .marketAverageReturnPercentage(marketAverageRateOfReturn)
            .build();
    }

    private double getRateOfReturn(AccountOverview accountOverview) {
        return calculateInternalRateOfReturn(accountOverview.getTransactions());
    }

    private double getRateOfReturn(AccountOverview accountOverview, FundValueProvider fundValueProvider) {
        BigDecimal virtualFundUnitsBought = BigDecimal.ZERO;
        List<Transaction> transactionsWithoutWithdrawal = withoutLastRow(accountOverview.getTransactions());
        for (Transaction transaction: transactionsWithoutWithdrawal) {
            BigDecimal fundValueAtTime = fundValueProvider.getFundValueClosestToTime(transaction.getCreatedAt()).getValue();
            BigDecimal currentlyBoughtVirtualFundUnits = transaction.getAmount().negate().divide(fundValueAtTime, MathContext.DECIMAL128);
            virtualFundUnitsBought = virtualFundUnitsBought.add(currentlyBoughtVirtualFundUnits);
        }

        BigDecimal finalVirtualFundPrice = fundValueProvider.getFundValueClosestToTime(accountOverview.getEndTime()).getValue();
        BigDecimal sellAmount = finalVirtualFundPrice.multiply(virtualFundUnitsBought);
        Transaction withdrawalRow = new Transaction(sellAmount, accountOverview.getEndTime());
        transactionsWithoutWithdrawal.add(withdrawalRow);

        return calculateInternalRateOfReturn(transactionsWithoutWithdrawal);
    }

    private double calculateInternalRateOfReturn(List<Transaction> transactions) {
        // wish the author of this great library used interfaces instead
        List<org.decampo.xirr.Transaction> xirrInternalTransactions = transactions
                .stream()
                .map(FundComparisonCalculatorService::xirrTransactionFromInternalTransaction)
                .collect(Collectors.toList());
        double result = new Xirr(xirrInternalTransactions).xirr();
        return roundPercentage(result);
    }

    private double roundPercentage(double percentage) {
        // ok to lose the precision here, doing presentational formatting here.
        return BigDecimal.valueOf(percentage).setScale(OUTPUT_SCALE, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    private static org.decampo.xirr.Transaction xirrTransactionFromInternalTransaction(Transaction transaction) {
        return new org.decampo.xirr.Transaction(transaction.getAmount().doubleValue(), Date.from(transaction.getCreatedAt()));
    }

    private static <T> List<T> withoutLastRow(List<T> list) {
        return list.subList(0, list.size() - 1);
    }
}
