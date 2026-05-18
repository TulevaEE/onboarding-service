package ee.tuleva.onboarding.investment.transaction.portfolio

import ee.tuleva.onboarding.investment.transaction.TransactionType
import ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisCalculator.ExecutionEvent
import ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisCalculator.PriorPosition
import spock.lang.Specification
import spock.lang.Unroll

import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

class CostBasisCalculatorSpec extends Specification {

    private static final String FUND_ISIN = "EE3600109435"
    private static final String INSTRUMENT_ISIN = "IE00BFNM3G45"
    private static final LocalDate DATE = LocalDate.of(2026, 5, 1)

    private CostBasisCalculator calculator = new CostBasisCalculator()

    @Unroll
    def "BUY: prior #priorQty @ #priorAvg + BUY #buyQty @ #buyPrice (commission #commission) => qty=#expectedQty avg=#expectedAvg total=#expectedTotal"() {
        given:
        def prior = Optional.of(new PriorPosition(new BigDecimal(priorQty), new BigDecimal(priorAvg)))
        def execs = [
                new ExecutionEvent(TransactionType.BUY, new BigDecimal(buyQty), new BigDecimal(buyPrice), new BigDecimal(commission))
        ]

        when:
        def result = calculator.calculate(prior, execs, FUND_ISIN, INSTRUMENT_ISIN, DATE)

        then:
        result.quantity.compareTo(new BigDecimal(expectedQty)) == 0
        result.avgUnitCost.compareTo(new BigDecimal(expectedAvg)) == 0
        result.totalCost.compareTo(new BigDecimal(expectedTotal)) == 0

        where:
        priorQty | priorAvg | buyQty | buyPrice | commission || expectedQty | expectedAvg   | expectedTotal
        "100000" | "10.00"  | "20000" | "11.00" | "50.00"    || "120000"    | "10.16708333" | "1220050.00"
        "0"      | "0"      | "1000"  | "5.00"  | "0"        || "1000"      | "5.00000000"  | "5000.00"
        "0"      | "0"      | "1000"  | "5.00"  | "10"       || "1000"      | "5.01000000"  | "5010.00"
    }

    def "BUY accumulates same-day multiple executions"() {
        given:
        def prior = Optional.of(new PriorPosition(new BigDecimal("0"), new BigDecimal("0")))
        def execs = [
                new ExecutionEvent(TransactionType.BUY, new BigDecimal("100"), new BigDecimal("10.00"), BigDecimal.ZERO),
                new ExecutionEvent(TransactionType.BUY, new BigDecimal("200"), new BigDecimal("11.00"), BigDecimal.ZERO),
                new ExecutionEvent(TransactionType.BUY, new BigDecimal("100"), new BigDecimal("12.00"), BigDecimal.ZERO)
        ]

        when:
        def result = calculator.calculate(prior, execs, FUND_ISIN, INSTRUMENT_ISIN, DATE)

        then:
        result.quantity.compareTo(new BigDecimal("400")) == 0
        result.totalCost.compareTo(new BigDecimal("4400.00")) == 0
        result.avgUnitCost.compareTo(new BigDecimal("11.00000000")) == 0
    }

    def "SELL: partial sell uses prior avg unit cost"() {
        given:
        def prior = Optional.of(new PriorPosition(new BigDecimal("1000"), new BigDecimal("10.00")))
        def execs = [
                new ExecutionEvent(TransactionType.SELL, new BigDecimal("400"), new BigDecimal("12.00"), BigDecimal.ZERO)
        ]

        when:
        def result = calculator.calculate(prior, execs, FUND_ISIN, INSTRUMENT_ISIN, DATE)

        then:
        result.quantity.compareTo(new BigDecimal("600")) == 0
        result.totalCost.compareTo(new BigDecimal("6000.00")) == 0
        result.avgUnitCost.compareTo(new BigDecimal("10.00000000")) == 0
    }

    def "SELL: full liquidation zeroes the row"() {
        given:
        def prior = Optional.of(new PriorPosition(new BigDecimal("1000"), new BigDecimal("10.00")))
        def execs = [
                new ExecutionEvent(TransactionType.SELL, new BigDecimal("1000"), new BigDecimal("12.00"), BigDecimal.ZERO)
        ]

        when:
        def result = calculator.calculate(prior, execs, FUND_ISIN, INSTRUMENT_ISIN, DATE)

        then:
        result.quantity.compareTo(BigDecimal.ZERO) == 0
        result.totalCost.compareTo(BigDecimal.ZERO) == 0
        result.avgUnitCost.compareTo(BigDecimal.ZERO) == 0
        result.deltaQuantity.compareTo(new BigDecimal("-1000")) == 0
    }

    def "SELL: over-sell clamps to zero with warning"() {
        given:
        def prior = Optional.of(new PriorPosition(new BigDecimal("100"), new BigDecimal("10.00")))
        def execs = [
                new ExecutionEvent(TransactionType.SELL, new BigDecimal("500"), new BigDecimal("12.00"), BigDecimal.ZERO)
        ]

        when:
        def result = calculator.calculate(prior, execs, FUND_ISIN, INSTRUMENT_ISIN, DATE)

        then:
        result.quantity.compareTo(BigDecimal.ZERO) == 0
        result.totalCost.compareTo(BigDecimal.ZERO) == 0
    }

    def "new ISIN: no prior, first BUY sets initial state"() {
        given:
        def execs = [
                new ExecutionEvent(TransactionType.BUY, new BigDecimal("500"), new BigDecimal("7.50"), new BigDecimal("25"))
        ]

        when:
        def result = calculator.calculate(Optional.empty(), execs, FUND_ISIN, INSTRUMENT_ISIN, DATE)

        then:
        result.quantity.compareTo(new BigDecimal("500")) == 0
        result.totalCost.compareTo(new BigDecimal("3775.00")) == 0
        result.avgUnitCost.compareTo(new BigDecimal("7.55000000")) == 0
        result.deltaQuantity.compareTo(new BigDecimal("500")) == 0
        result.source == "DERIVED"
    }

    def "no executions and no prior: returns zeroed position"() {
        when:
        def result = calculator.calculate(Optional.empty(), [], FUND_ISIN, INSTRUMENT_ISIN, DATE)

        then:
        result.quantity.compareTo(BigDecimal.ZERO) == 0
        result.totalCost.compareTo(BigDecimal.ZERO) == 0
        result.avgUnitCost.compareTo(BigDecimal.ZERO) == 0
    }

    def "carry-over: no executions but prior exists keeps prior values"() {
        given:
        def prior = Optional.of(new PriorPosition(new BigDecimal("100"), new BigDecimal("10.00")))

        when:
        def result = calculator.calculate(prior, [], FUND_ISIN, INSTRUMENT_ISIN, DATE)

        then:
        result.quantity.compareTo(new BigDecimal("100")) == 0
        result.totalCost.compareTo(new BigDecimal("1000.00")) == 0
        result.avgUnitCost.compareTo(new BigDecimal("10.00000000")) == 0
        result.deltaQuantity.compareTo(BigDecimal.ZERO) == 0
    }

    def "commission included in cost basis for BUY"() {
        given:
        def prior = Optional.of(new PriorPosition(new BigDecimal("0"), new BigDecimal("0")))
        def execs = [
                new ExecutionEvent(TransactionType.BUY, new BigDecimal("100"), new BigDecimal("10.00"), new BigDecimal("100"))
        ]

        when:
        def result = calculator.calculate(prior, execs, FUND_ISIN, INSTRUMENT_ISIN, DATE)

        then:
        result.totalCost.compareTo(new BigDecimal("1100.00")) == 0
        result.avgUnitCost.compareTo(new BigDecimal("11.00000000")) == 0
    }

    def "mixed BUY then SELL same day"() {
        given:
        def prior = Optional.of(new PriorPosition(new BigDecimal("0"), new BigDecimal("0")))
        def execs = [
                new ExecutionEvent(TransactionType.BUY, new BigDecimal("1000"), new BigDecimal("10.00"), BigDecimal.ZERO),
                new ExecutionEvent(TransactionType.SELL, new BigDecimal("400"), new BigDecimal("12.00"), BigDecimal.ZERO)
        ]

        when:
        def result = calculator.calculate(prior, execs, FUND_ISIN, INSTRUMENT_ISIN, DATE)

        then:
        result.quantity.compareTo(new BigDecimal("600")) == 0
        result.totalCost.compareTo(new BigDecimal("6000.00")) == 0
        result.avgUnitCost.compareTo(new BigDecimal("10.00000000")) == 0
    }

    def "null commission treated as zero"() {
        given:
        def prior = Optional.empty()
        def execs = [
                new ExecutionEvent(TransactionType.BUY, new BigDecimal("100"), new BigDecimal("10.00"), null)
        ]

        when:
        def result = calculator.calculate(prior, execs, FUND_ISIN, INSTRUMENT_ISIN, DATE)

        then:
        result.totalCost.compareTo(new BigDecimal("1000.00")) == 0
    }
}
