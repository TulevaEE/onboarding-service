package ee.tuleva.onboarding.swedbank.statement;

import static ee.swedbank.gateway.iso.response.BalanceType12Code.*;
import static ee.swedbank.gateway.iso.response.CreditDebitCode.CRDT;

import ee.swedbank.gateway.iso.response.BalanceType12Code;
import ee.swedbank.gateway.iso.response.CashBalance3;
import ee.tuleva.onboarding.swedbank.converter.XmlGregorianCalendarConverterToLocalDate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

public record BankStatementBalance(StatementBalanceType type, LocalDate time, BigDecimal balance) {

  public enum StatementBalanceType {
    OPEN(OPBD), // Opening booked balance
    CLOSE(CLBD), // Closing booked balance
    OPEN_AVAILABLE(OPAV), // Opening available balance
    INTERIM_AVAILABLE(ITAV), // Interim available balance
    CLOSING_AVAILABLE(CLAV), // Closing available balance
    FORWARD_AVAILABLE(FWAV), // Forward available balance
    INTERIM_BOOKED(ITBD), // Interim booked balance
    PREVIOUSLY_CLOSED(PRCD), // Previously closed booked balance
    EXPECTED(XPCD), // Expected balance
    INFORMATION(INFO); // Informational balance

    private final BalanceType12Code balanceCode;

    StatementBalanceType(BalanceType12Code balanceTypeCode) {
      this.balanceCode = balanceTypeCode;
    }

    public static StatementBalanceType fromBalanceCode(BalanceType12Code balanceTypeCode) {
      return Arrays.stream(StatementBalanceType.values())
          .filter(balanceType -> balanceType.balanceCode.equals(balanceTypeCode))
          .findFirst()
          .orElse(null); // TODO reserved party null balance code?
      /*.orElseThrow(
      () -> new IllegalArgumentException("Cannot match balance type " + balanceTypeCode));*/
    }
  }

  public static BankStatementBalance from(CashBalance3 balance) {
    var dateConverter = new XmlGregorianCalendarConverterToLocalDate();
    var statementBalanceType =
        StatementBalanceType.fromBalanceCode(
            balance.getTp().getCdOrPrtry().getCd()); // TODO reserved party = null?

    // handle dateTime here as well?
    var date = dateConverter.convert(balance.getDt().getDt());

    var creditOrDebit = balance.getCdtDbtInd();
    var creditDebitCoefficient = creditOrDebit == CRDT ? BigDecimal.ONE : new BigDecimal("-1.0");
    var balanceAmount = balance.getAmt().getValue().multiply(creditDebitCoefficient);

    return new BankStatementBalance(statementBalanceType, date, balanceAmount);
  }
}
