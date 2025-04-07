package ee.tuleva.onboarding.analytics.fund;

import ee.tuleva.onboarding.epis.transaction.FundTransactionDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class FundTransactionFixture {

  public static class Dto {
    public static final String NEW_ISIN = "EE3600019832";
    public static final String NEW_PERSONAL_ID = "50001010000";
    public static final String NEW_TRANSACTION_TYPE = "PAYMENT";
    public static final BigDecimal NEW_AMOUNT = BigDecimal.valueOf(100.50);
    public static final BigDecimal NEW_UNIT_AMOUNT = BigDecimal.valueOf(10.1234);
    public static final LocalDate NEW_TRANSACTION_DATE = LocalDate.of(2025, 4, 1);

    public static final String DUPLICATE_ISIN = "EE3600109435";
    public static final String DUPLICATE_PERSONAL_ID = "60001010000";
    public static final String DUPLICATE_TRANSACTION_TYPE = "RECEIPT";
    public static final BigDecimal DUPLICATE_AMOUNT = BigDecimal.valueOf(200.75);
    public static final BigDecimal DUPLICATE_UNIT_AMOUNT = BigDecimal.valueOf(20.5678);
    public static final LocalDate DUPLICATE_TRANSACTION_DATE = LocalDate.of(2025, 4, 2);

    public static FundTransactionDto newTransactionDto() {
      return FundTransactionDto.builder()
          .date(NEW_TRANSACTION_DATE)
          .personName("Mari Uus")
          .personId(NEW_PERSONAL_ID)
          .pensionAccount("1199119911")
          .country("EE")
          .transactionType(NEW_TRANSACTION_TYPE)
          .purpose("PENSION_PAYMENT")
          .applicationType("APPLICATION")
          .unitAmount(NEW_UNIT_AMOUNT)
          .price(BigDecimal.valueOf(1.123))
          .nav(BigDecimal.valueOf(1.125))
          .amount(NEW_AMOUNT)
          .serviceFee(BigDecimal.valueOf(0.05))
          .build();
    }

    public static FundTransactionDto duplicateTransactionDto() {
      return FundTransactionDto.builder()
          .date(DUPLICATE_TRANSACTION_DATE)
          .personName("Jaan Kordu")
          .personId(DUPLICATE_PERSONAL_ID)
          .pensionAccount("2288228822")
          .country("EE")
          .transactionType(DUPLICATE_TRANSACTION_TYPE)
          .purpose("FUND_TRANSFER")
          .applicationType("INTERNAL")
          .unitAmount(DUPLICATE_UNIT_AMOUNT)
          .price(BigDecimal.valueOf(1.050))
          .nav(BigDecimal.valueOf(1.052))
          .amount(DUPLICATE_AMOUNT)
          .serviceFee(BigDecimal.valueOf(0.10))
          .build();
    }
  }

  public static FundTransaction.FundTransactionBuilder exampleTransactionBuilder(
      String isin, LocalDateTime dateCreated) {
    return FundTransaction.builder()
        .isin(isin)
        .transactionDate(Dto.NEW_TRANSACTION_DATE)
        .personName("Mari Uus")
        .personalId(Dto.NEW_PERSONAL_ID)
        .pensionAccount("1199119911")
        .country("EE")
        .transactionType(Dto.NEW_TRANSACTION_TYPE)
        .purpose("PENSION_PAYMENT")
        .applicationType("APPLICATION")
        .unitAmount(Dto.NEW_UNIT_AMOUNT)
        .price(BigDecimal.valueOf(1.123))
        .nav(BigDecimal.valueOf(1.125))
        .amount(Dto.NEW_AMOUNT)
        .serviceFee(BigDecimal.valueOf(0.05))
        .dateCreated(dateCreated);
  }

  public static FundTransaction.FundTransactionBuilder anotherExampleTransactionBuilder(
      String isin, LocalDateTime dateCreated) {
    return FundTransaction.builder()
        .isin(isin)
        .transactionDate(Dto.DUPLICATE_TRANSACTION_DATE)
        .personName("Jaan Kordu")
        .personalId(Dto.DUPLICATE_PERSONAL_ID)
        .pensionAccount("2288228822")
        .country("EE")
        .transactionType(Dto.DUPLICATE_TRANSACTION_TYPE)
        .purpose("FUND_TRANSFER")
        .applicationType("INTERNAL")
        .unitAmount(Dto.DUPLICATE_UNIT_AMOUNT)
        .price(BigDecimal.valueOf(1.050))
        .nav(BigDecimal.valueOf(1.052))
        .amount(Dto.DUPLICATE_AMOUNT)
        .serviceFee(BigDecimal.valueOf(0.10))
        .dateCreated(dateCreated);
  }
}
