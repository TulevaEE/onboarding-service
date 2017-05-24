package ee.tuleva.onboarding.comparisons;

import ee.tuleva.onboarding.account.AccountStatementService;
import ee.tuleva.onboarding.account.FundBalance;
import ee.tuleva.onboarding.comparisons.exceptions.IsinNotFoundException;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ComparisonService {

    private final FundRepository fundRepository;

    private final AccountStatementService accountStatementService;

    private final UserService userService;

    /**
     * Merging actual user data to calculator input.
     * @param in
     * @param userId
     * @return
     */
    public ComparisonResponse compare(ComparisonCommand in, Long userId) throws IsinNotFoundException {
        User user = userService.getById(userId);
        in.setAge(user.getAge());

        List<FundBalance> balances = getBalances(user);
        in.setCurrentCapitals(new HashMap<String, BigDecimal>());
        balances.forEach( balance -> { in.getCurrentCapitals().put(balance.getFund().getIsin(), balance.getValue()); });

        // todo still getting fee rates from same report although we agreed to refactor away that from accountStatement

        in.setManagementFeeRates(new HashMap<String, BigDecimal>());
        balances.forEach( balance -> {
            in.getManagementFeeRates().put(balance.getFund().getIsin(), balance.getFund().getManagementFeeRate());
            if (balance.isActiveContributions()) {
                in.setActiveFundIsin(balance.getFund().getIsin());
            }
        });

        // todo as long as Tuleva funds have same managementfeerate then just taking one.
        Fund tulevaFundToCompareTo = fundRepository.findByIsin(in.getIsinTo());
        in.getManagementFeeRates().put(tulevaFundToCompareTo.getIsin(), tulevaFundToCompareTo.getManagementFeeRate());

        if(in.monthlyWage == null) {
            throw new IllegalArgumentException("monthlyWage can not be null");
        }

        log.info(in.toString());

        return getComparisonResponse(in);
    }

    private List<FundBalance> getBalances(User user) {
        return accountStatementService.getMyPensionAccountStatement(user, UUID.randomUUID());
    }

    public ComparisonResponse getComparisonResponse(ComparisonCommand in) {

        FutureValue currentFundValues = calculateFVForCurrentPlan(in);
        FutureValue newFundValues = calculateFVForSwitchPlan(in);

        return ComparisonResponse.builder()
                .newFundFutureValue(round(newFundValues.getWithFee()))
                .currentFundFutureValue(round(currentFundValues.getWithFee()))
                .newFundFee(
                        round(newFundValues.withoutFee.subtract(newFundValues.withFee))
                )
                .currentFundFee(
                        round(currentFundValues.withoutFee.subtract(currentFundValues.withFee))
                )
                .build();
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Means all current fund capitals are converted to Tuleva.
     */
    protected FutureValue calculateFVForSwitchPlan(ComparisonCommand in) {
        int yearsToWork = in.ageOfRetirement - in.age;

        BigDecimal currentCapitals = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : in.getCurrentCapitals().entrySet()) {
            String isin = entry.getKey();
            BigDecimal currentCapital = in.getCurrentCapitals().get(isin);

            if (currentCapital == null) {
                throw new RuntimeException("Missing current capital for fund " + isin);
            }
            currentCapitals = currentCapitals.add(currentCapital);
        }

        BigDecimal fundManagementFee = in.getManagementFeeRates().get(in.isinTo);
        BigDecimal effectiveManagementFee = in.isTulevaMember ? fundManagementFee.subtract(in.getTulevaMemberBonus()) : fundManagementFee;
        BigDecimal annualInterestRate = in.getReturnRate().subtract(effectiveManagementFee);
        BigDecimal capitalFv = fvCompoundInterest(currentCapitals, annualInterestRate, yearsToWork);
        BigDecimal capitalFvWithoutFee = fvCompoundInterest(currentCapitals, in.getReturnRate(), yearsToWork);
        BigDecimal yearlyContribution = in.monthlyWage.multiply(new BigDecimal(12)).multiply(in.secondPillarContributionRate);
        BigDecimal annuityFv = fvGrowingAnnuity(yearlyContribution, annualInterestRate, in.annualSalaryGainRate, yearsToWork);
        BigDecimal annuityFvWithoutFee = fvGrowingAnnuity(yearlyContribution, in.getReturnRate(), in.annualSalaryGainRate, yearsToWork);

        return FutureValue.builder()
                .withFee(capitalFv.add(annuityFv))
                .withoutFee(capitalFvWithoutFee.add(annuityFvWithoutFee))
                .build();
    }

// todo kas switch plan on fondide ületoomisega või mitte? tehtud praegu ületoomisega.
// todo activeisini != et on capitali rida, värskelt fondi vahetanud tuleb panna comparisoni eraldi
    public FutureValue calculateFVForCurrentPlan(ComparisonCommand in) {
        int yearsToWork = in.ageOfRetirement - in.age;

        // calculating static FutureValue for everything in balance except active and isinTo ones.
        BigDecimal fv = getFutureValueWithFees(in, yearsToWork);
        BigDecimal fvWithoutFees = getFutureValueWithoutFees(in, yearsToWork);

        // contribution part, applies to active fund only, means in.activeisin
        BigDecimal yearlyContribution = in.monthlyWage.multiply(new BigDecimal(12)).multiply(in.secondPillarContributionRate);
        BigDecimal contributionMgmntFee = in.getReturnRate().subtract(in.getManagementFeeRates().get(in.activeFundIsin));
        BigDecimal contributionWithFees = fvGrowingAnnuity(yearlyContribution, contributionMgmntFee, in.annualSalaryGainRate, yearsToWork);
        BigDecimal contributionWithoutFees = fvGrowingAnnuity(yearlyContribution, in.getReturnRate(), in.annualSalaryGainRate, yearsToWork);

        return FutureValue.builder()
                .withFee(fv.add(contributionWithFees))
                .withoutFee(fvWithoutFees.add(contributionWithoutFees))
                .build();
    }

    private BigDecimal getFutureValueWithFees(ComparisonCommand in, int yearsToWork) {
        BigDecimal fv = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : in.getCurrentCapitals().entrySet()) {
            String isin = entry.getKey();
            BigDecimal currentCapital = in.getCurrentCapitals().get(isin); // aka tc
            BigDecimal annualInterestRate = in.getReturnRate().subtract(in.getManagementFeeRates().get(isin)); // aka r
            BigDecimal entryFV = fvCompoundInterest(currentCapital, annualInterestRate, yearsToWork);
            fv = fv.add(entryFV);
        }
        return fv;
    }

    private BigDecimal getFutureValueWithoutFees(ComparisonCommand in, int yearsToWork) {
        BigDecimal fv = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : in.getCurrentCapitals().entrySet()) {
            String isin = entry.getKey();
            BigDecimal currentCapital = in.getCurrentCapitals().get(isin); // aka tc
            BigDecimal entryFV = fvCompoundInterest(currentCapital, in.getReturnRate(), yearsToWork);
            fv = fv.add(entryFV);
        }
        return fv;
    }

    /**
     * Growning annuity calculator
     * @param c initial amount invested first time
     * @param r interest in percent
     * @param g initial amount growth in percent
     * @param n years for future value
     * @return
     */
    public static BigDecimal fvGrowingAnnuity(BigDecimal c, BigDecimal r, BigDecimal g, int n) {
        BigDecimal f1 = c.divide(r.subtract(g), 6, RoundingMode.HALF_UP);
        return f1.multiply(growthFactor(r, n).subtract(growthFactor(g, n)));
    }

    public static BigDecimal growthFactor(BigDecimal r, int n) {
        return BigDecimal.ONE.add(r).pow(n);
    }

    /**
     * Future value compound interest, yearly interest.
     * @param principal investment amount or initial deposit
     * @param annualInterestRate annual interest rate
     * @param yearsToInvest the number of years the money is invested for
     * @return the future value of the investment, including interest
     */
    public static BigDecimal fvCompoundInterest(BigDecimal principal, BigDecimal annualInterestRate, int yearsToInvest) {
        return principal.multiply(annualInterestRate.add(BigDecimal.ONE).pow(yearsToInvest));
    }

    @Builder
    @Getter
    static class FutureValue {
        private BigDecimal withFee;
        private BigDecimal withoutFee;
    }
}
