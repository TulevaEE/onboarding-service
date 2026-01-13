package ee.tuleva.onboarding.swedbank.statement;

import static ee.tuleva.onboarding.banking.iso20022.camt052.BalanceType12Code.*;
import static ee.tuleva.onboarding.banking.iso20022.camt052.CreditDebitCode.CRDT;

import ee.tuleva.onboarding.banking.iso20022.camt052.BalanceType12Code;
import ee.tuleva.onboarding.banking.iso20022.camt052.CashBalance3;
import ee.tuleva.onboarding.banking.iso20022.camt053.CreditDebitCode;
import ee.tuleva.onboarding.swedbank.converter.XmlGregorianCalendarConverterToLocalDate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

public record BankStatementBalance(StatementBalanceType type, LocalDate time, BigDecimal balance) {

  public enum StatementBalanceType {
    OPEN(
        OPBD,
        ee.tuleva.onboarding.banking.iso20022.camt053.BalanceType12Code
            .OPBD), // Opening booked balance
    CLOSE(
        CLBD,
        ee.tuleva.onboarding.banking.iso20022.camt053.BalanceType12Code
            .CLBD), // Closing booked balance
    OPEN_AVAILABLE(
        OPAV,
        ee.tuleva.onboarding.banking.iso20022.camt053.BalanceType12Code
            .OPAV), // Opening available balance
    INTERIM_AVAILABLE(
        ITAV,
        ee.tuleva.onboarding.banking.iso20022.camt053.BalanceType12Code
            .ITAV), // Interim available balance
    CLOSING_AVAILABLE(
        CLAV,
        ee.tuleva.onboarding.banking.iso20022.camt053.BalanceType12Code
            .CLAV), // Closing available balance
    FORWARD_AVAILABLE(
        FWAV,
        ee.tuleva.onboarding.banking.iso20022.camt053.BalanceType12Code
            .FWAV), // Forward available balance
    INTERIM_BOOKED(
        ITBD,
        ee.tuleva.onboarding.banking.iso20022.camt053.BalanceType12Code
            .ITBD), // Interim booked balance
    PREVIOUSLY_CLOSED(
        PRCD,
        ee.tuleva.onboarding.banking.iso20022.camt053.BalanceType12Code
            .PRCD), // Previously closed booked balance
    EXPECTED(
        XPCD,
        ee.tuleva.onboarding.banking.iso20022.camt053.BalanceType12Code.XPCD), // Expected balance
    INFORMATION(
        INFO,
        ee.tuleva.onboarding.banking.iso20022.camt053.BalanceType12Code
            .INFO); // Informational balance

    private final BalanceType12Code reportBalanceCode;
    private final ee.tuleva.onboarding.banking.iso20022.camt053.BalanceType12Code
        statementBalanceCode;

    StatementBalanceType(
        BalanceType12Code balanceTypeCode,
        ee.tuleva.onboarding.banking.iso20022.camt053.BalanceType12Code statementBalanceCode) {
      this.reportBalanceCode = balanceTypeCode;
      this.statementBalanceCode = statementBalanceCode;
    }

    public static StatementBalanceType fromBalanceCode(BalanceType12Code balanceTypeCode) {
      return Arrays.stream(StatementBalanceType.values())
          .filter(balanceType -> balanceType.reportBalanceCode.equals(balanceTypeCode))
          .findFirst()
          .orElse(null); // TODO reserved party null balance code?
      /*.orElseThrow(
      () -> new IllegalArgumentException("Cannot match balance type " + balanceTypeCode));*/
    }

    public static StatementBalanceType fromBalanceCode(
        ee.tuleva.onboarding.banking.iso20022.camt053.BalanceType12Code balanceTypeCode) {
      return Arrays.stream(StatementBalanceType.values())
          .filter(balanceType -> balanceType.statementBalanceCode.equals(balanceTypeCode))
          .findFirst()
          .orElse(null); // TODO reserved party null balance code?
      /*.orElseThrow(
      () -> new IllegalArgumentException("Cannot match balance type " + balanceTypeCode));*/
    }
  }

  static BankStatementBalance from(CashBalance3 balance) {
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

  static BankStatementBalance from(
      ee.tuleva.onboarding.banking.iso20022.camt053.CashBalance3 balance) {
    var dateConverter = new XmlGregorianCalendarConverterToLocalDate();
    var statementBalanceType =
        StatementBalanceType.fromBalanceCode(balance.getTp().getCdOrPrtry().getCd());

    // handle dateTime here as well?
    var date = dateConverter.convert(balance.getDt().getDt());

    var creditOrDebit = balance.getCdtDbtInd();
    var creditDebitCoefficient =
        creditOrDebit == CreditDebitCode.CRDT ? BigDecimal.ONE : new BigDecimal("-1.0");
    var balanceAmount = balance.getAmt().getValue().multiply(creditDebitCoefficient);

    return new BankStatementBalance(statementBalanceType, date, balanceAmount);
  }
}
