package ee.tuleva.onboarding.income;


import ee.eesti.xtee6.kpr.PensionAccountTransactionResponseType.Money.Transaction;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Calculates average salary based on pension second pillar payments.
 */
public class AverageIncomeCalculator {

    /**
     * - Should be aware of the missing payments at the beginning - someone just started her career.
     * - Missing payments at the end of period - lost job? return no salary.
     * - Missing payments in the middle - accountant probably paid social tax later. Considering it ok.
     */
    public static Money calculate(List<Transaction> trns) {
        BigDecimal emtaPaymentsTotal = new BigDecimal(0);

        for (Transaction trn : trns) {
            assert "EUR".equals(trn.getCurrency()); // last year transactions only here

            if (trn.getSum().compareTo(BigDecimal.ZERO) > 0) {
                emtaPaymentsTotal = emtaPaymentsTotal.add(trn.getSum());
            }
        }

        BigDecimal averagePayment = emtaPaymentsTotal.divide(new BigDecimal("12"));
        BigDecimal averageSalary = calculateSalaryFromPensionPayment(averagePayment);

        return Money.builder()
                .currency("EUR")
                .amount(averageSalary)
                .build();
    }


    /**
     * Pension payment is 2 + 4 = 6% of bruto salary.
     */
    static BigDecimal calculateSalaryFromPensionPayment(BigDecimal pensionPaymentAmount) {
        return pensionPaymentAmount.divide(new BigDecimal("0.06"));
    }

}
