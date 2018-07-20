package ee.tuleva.onboarding.comparisons;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.comparisons.fundvalue.ComparisonFund;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.comparisons.overview.AccountOverview;
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider;
import ee.tuleva.onboarding.comparisons.overview.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.decampo.xirr.Xirr;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FundComparisonCalculatorService {

    private final AccountOverviewProvider accountOverviewProvider;
    private final FundValueProvider fundValueProvider;

    private static final int OUTPUT_SCALE = 4;

    public FundComparison calculateComparison(Person person, Instant startTime) {
        AccountOverview overview = accountOverviewProvider.getAccountOverview(person, startTime);

        // TODO: Remove when feature goes public
        log.info("Writing out account overview");
        log.info(overview.toString());

        return calculateForAccountOverview(overview);
    }

    private FundComparison calculateForAccountOverview(AccountOverview accountOverview) {
        double actualRateOfReturn = getRateOfReturn(accountOverview);
        double estonianAverageRateOfReturn = getRateOfReturn(accountOverview, ComparisonFund.EPI);
        double marketAverageRateOfReturn = getRateOfReturn(accountOverview, ComparisonFund.MARKET);

        return FundComparison.builder()
            .actualReturnPercentage(actualRateOfReturn)
            .estonianAverageReturnPercentage(estonianAverageRateOfReturn)
            .marketAverageReturnPercentage(marketAverageRateOfReturn)
            .build();
    }

    private double getRateOfReturn(AccountOverview accountOverview) {
        List<Transaction> purchaseTransactions = getPurchaseTransactions(accountOverview);

        // TODO: Remove when feature goes public
        log.info("Writing out purchase transactions");
        log.info(purchaseTransactions.toString());

        return calculateReturn(purchaseTransactions, accountOverview.getEndingBalance(), accountOverview.getEndTime());
    }

    private double getRateOfReturn(AccountOverview accountOverview, ComparisonFund comparisonFund) {
        List<Transaction> purchaseTransactions = getPurchaseTransactions(accountOverview);

        BigDecimal virtualFundUnitsBought = BigDecimal.ZERO;
        for (Transaction transaction: purchaseTransactions) {
            Optional<FundValue> fundValueAtTime = fundValueProvider.getFundValueClosestToTime(comparisonFund, transaction.getCreatedAt());
            if (!fundValueAtTime.isPresent()) {
                return 0;
            }
            BigDecimal fundPriceAtTime = fundValueAtTime.get().getValue();
            BigDecimal currentlyBoughtVirtualFundUnits = transaction.getAmount().divide(fundPriceAtTime, MathContext.DECIMAL128);
            virtualFundUnitsBought = virtualFundUnitsBought.add(currentlyBoughtVirtualFundUnits);
        }
        Optional<FundValue> finalVirtualFundValue = fundValueProvider.getFundValueClosestToTime(comparisonFund, accountOverview.getEndTime());
        if (!finalVirtualFundValue.isPresent()) {
            return 0;
        }
        BigDecimal finalVirtualFundPrice = finalVirtualFundValue.get().getValue();
        BigDecimal sellAmount = finalVirtualFundPrice.multiply(virtualFundUnitsBought);

        return calculateReturn(purchaseTransactions, sellAmount, accountOverview.getEndTime());
    }

    private List<Transaction> getPurchaseTransactions(AccountOverview accountOverview) {
        List<Transaction> transactions = accountOverview.getTransactions();
        Transaction beginningTransaction = new Transaction(accountOverview.getBeginningBalance(), accountOverview.getStartTime());

        // TODO: Remove when feature goes public
        log.info("Writing out beginning transaction");
        log.info(beginningTransaction.toString());

        List<Transaction> purchaseTransactions = new ArrayList<>();
        purchaseTransactions.add(beginningTransaction);
        purchaseTransactions.addAll(transactions);

        return purchaseTransactions;
    }

    private List<Transaction> negateTransactionAmounts(List<Transaction> transactions) {
        return transactions
                .stream()
                .map(FundComparisonCalculatorService::negateTransactionAmount)
                .collect(Collectors.toList());
    }

    private static Transaction negateTransactionAmount(Transaction transaction) {
        return new Transaction(transaction.getAmount().negate(), transaction.getCreatedAt());
    }

    private double calculateReturn(List<Transaction> purchaseTransactions, BigDecimal endingBalance, Instant endTime) {
        List<Transaction> negatedTransactions = negateTransactionAmounts(purchaseTransactions);
        Transaction endingTransaction = new Transaction(endingBalance, endTime);

        List<Transaction> internalTransactions = new ArrayList<>();
        internalTransactions.addAll(negatedTransactions);
        internalTransactions.add(endingTransaction);

        // TODO: Remove when feature goes public
        log.info("Writing out internal transactions");
        log.info(internalTransactions.toString());

        return calculateInternalRateOfReturn(internalTransactions);
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
}
