package ee.tuleva.onboarding.comparisons;

import ee.tuleva.onboarding.account.AccountStatementService;
import ee.tuleva.onboarding.account.FundBalance;
import ee.tuleva.onboarding.comparisons.exceptions.IsinNotFoundException;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.income.AverageSalaryService;
import ee.tuleva.onboarding.income.Money;
import ee.tuleva.onboarding.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ComparisonService {

    @Autowired
    private FundRepository fundRepository;

    @Autowired
    private AccountStatementService accountStatementService;

    @Autowired
    private AverageSalaryService averageSalaryService;

    /**
     * Merging actual user data to calculator input.
     * @param in
     * @param user
     * @return
     */
    public Money compare(ComparisonCommand in, User user) throws IsinNotFoundException {
        in.setAge(user.getAge());

        List<FundBalance> balances = accountStatementService.getMyPensionAccountStatement(user, null);
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

        Money averageSalary = averageSalaryService.getMyAverageSalary(user.getPersonalCode());
        in.setMonthlyWage(averageSalary.getAmount());

        System.out.println(in);

        return this.compare(in);
    }

    /**
     * Calculator that takes into account potential Tuleva fund holder current personal, fund and legal data
     * (Most in {@link ComparisonCommand}) and calculates potential gain in money for the age of retirement.
     * Comparison is done between current plan and switch to Tuleva fund.
     *
     * todo does it has effect to take into account leaving fee from some funds?
     */
    public Money compare(ComparisonCommand in) throws IsinNotFoundException {
        BigDecimal fvTakenFeesDifference = calculateTotalFeeSaved(in);

        return Money.builder()
                .amount(fvTakenFeesDifference)
                .currency("EUR")
                .build();
    }

    public BigDecimal calculateTotalFeeSaved(ComparisonCommand in) {
        BigDecimal theDifference = calculateFVForSwitchPlan(in).subtract(calculateFVForCurrentPlan(in));
        BigDecimal totalFee = theDifference.setScale(2, RoundingMode.HALF_UP);
        return totalFee;
    }

    private static BigDecimal fv(BigDecimal yearlyContribution,
                                 int yearsToWork,
                                 BigDecimal totalCapital,
                                 BigDecimal annualSalaryGainRate,
                                 BigDecimal rate) {
        BigDecimal rateOnePlus = rate.add(BigDecimal.ONE);
        BigDecimal overOneAnnualSalaryGainRate = annualSalaryGainRate.add(BigDecimal.ONE);

        return yearlyContribution
                .multiply( rateOnePlus.pow(yearsToWork)
                        .subtract(overOneAnnualSalaryGainRate.pow(yearsToWork))
                )
                .divide( rate.subtract(annualSalaryGainRate) )
                .add( totalCapital.multiply( rateOnePlus.pow(yearsToWork)) );
    }


    /**
     * Means all current fund capitals are converted to Tuleva.
     */
    protected BigDecimal calculateFVForSwitchPlan(ComparisonCommand in) {
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

        BigDecimal annualInterestRate = in.getReturnRate().subtract(in.getManagementFeeRates().get(in.isinTo));
        BigDecimal capitalFv = fvCompoundInterest(currentCapitals, annualInterestRate, yearsToWork);
        BigDecimal yearlyContribution = in.monthlyWage.multiply(new BigDecimal(12)).multiply(in.secondPillarContributionRate);
        BigDecimal annuityFv = fvGrowingAnnuity(yearlyContribution, annualInterestRate, in.annualSalaryGainRate, yearsToWork);

        return capitalFv.add(annuityFv);
    }

// todo kas switch plan on fondide ületoomisega või mitte? tehtud praegu ületoomisega.

// todo activeisini != et on capitali rida, värskelt fondi vahetanud tuleb panna comparisoni eraldi
    public BigDecimal calculateFVForCurrentPlan(ComparisonCommand in) {
        int yearsToWork = in.ageOfRetirement - in.age;
        BigDecimal fv = BigDecimal.ZERO;
        // calculating static FutureValue for everything in balance except active and isinTo ones.
        for (Map.Entry<String, BigDecimal> entry : in.getCurrentCapitals().entrySet()) {
            String isin = entry.getKey();
            BigDecimal currentCapital = in.getCurrentCapitals().get(isin); // aka tc
            BigDecimal annualInterestRate = in.getReturnRate().subtract(in.getManagementFeeRates().get(isin)); // aka r
            BigDecimal entryFV = fvCompoundInterest(currentCapital, annualInterestRate, yearsToWork);
            fv = fv.add(entryFV);
        }

        // contribution part, applies to active fund only, means in.activeisin
        BigDecimal yearlyContribution = in.monthlyWage.multiply(new BigDecimal(12)).multiply(in.secondPillarContributionRate);
        BigDecimal contributionMgmntFee = in.getReturnRate().subtract(in.getManagementFeeRates().get(in.activeFundIsin));
        BigDecimal contribution = fvGrowingAnnuity(yearlyContribution, contributionMgmntFee, in.annualSalaryGainRate, yearsToWork);
        fv = fv.add(contribution);
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
}
