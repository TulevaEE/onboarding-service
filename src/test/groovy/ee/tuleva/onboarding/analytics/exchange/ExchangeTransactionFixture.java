package ee.tuleva.onboarding.analytics.exchange;

import ee.tuleva.onboarding.epis.transaction.ExchangeTransactionDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ExchangeTransactionFixture {

  public static ExchangeTransaction.ExchangeTransactionBuilder exampleTransactionBuilder() {
    return ExchangeTransaction.builder()
        .reportingDate(LocalDate.of(2025, 1, 1))
        .securityFrom("SEC_FROM_FIXTURE")
        .securityTo("SEC_TO_FIXTURE")
        .fundManagerFrom("FUND_MGR_FROM_FIXTURE")
        .fundManagerTo("FUND_MGR_TO_FIXTURE")
        .code("SAMPLE_CODE_FIXTURE")
        .firstName("John")
        .name("Doe")
        .percentage(BigDecimal.valueOf(2.5))
        .unitAmount(BigDecimal.valueOf(100.00))
        .dateCreated(LocalDateTime.now());
  }

  public static ExchangeTransaction exampleTransaction() {
    return exampleTransactionBuilder().build();
  }

  public static ExchangeTransaction.ExchangeTransactionBuilder anotherExampleTransactionBuilder() {
    return ExchangeTransaction.builder()
        .reportingDate(LocalDate.of(2025, 1, 2))
        .securityFrom("SEC_FROM_OTHER")
        .securityTo("SEC_TO_OTHER")
        .fundManagerFrom("FUND_MGR_FROM_OTHER")
        .fundManagerTo("FUND_MGR_TO_OTHER")
        .code("OTHER_CODE_FIXTURE")
        .firstName("Jane")
        .name("Smith")
        .percentage(BigDecimal.valueOf(3.7))
        .unitAmount(BigDecimal.valueOf(200.50))
        .dateCreated(LocalDateTime.now());
  }

  public static ExchangeTransaction anotherExampleTransaction() {
    return anotherExampleTransactionBuilder().build();
  }

  public static class Dto {
    public static final String NEW_SECURITY_FROM = "SECURITY_FROM_NEW";
    public static final String NEW_SECURITY_TO = "SECURITY_TO_NEW";
    public static final String NEW_CODE = "NEW_CODE_123";

    public static final String DUPLICATE_SECURITY_FROM = "SECURITY_FROM_DUP";
    public static final String DUPLICATE_SECURITY_TO = "SECURITY_TO_DUP";
    public static final String DUPLICATE_CODE = "DUPLICATE_CODE";

    public static ExchangeTransactionDto newTransactionDto() {
      return ExchangeTransactionDto.builder()
          .securityFrom(NEW_SECURITY_FROM)
          .securityTo(NEW_SECURITY_TO)
          .fundManagerFrom("FUND_MGR_FROM_NEW")
          .fundManagerTo("FUND_MGR_TO_NEW")
          .code(NEW_CODE)
          .firstName("John")
          .name("Newman")
          .percentage(BigDecimal.valueOf(2.5))
          .unitAmount(BigDecimal.valueOf(10.0))
          .build();
    }

    public static ExchangeTransactionDto duplicateTransactionDto() {
      return ExchangeTransactionDto.builder()
          .securityFrom(DUPLICATE_SECURITY_FROM)
          .securityTo(DUPLICATE_SECURITY_TO)
          .fundManagerFrom("FUND_MGR_FROM_DUP")
          .fundManagerTo("FUND_MGR_TO_DUP")
          .code(DUPLICATE_CODE)
          .firstName("Jane")
          .name("Duplicate")
          .percentage(BigDecimal.valueOf(5.0))
          .unitAmount(BigDecimal.valueOf(50.0))
          .build();
    }
  }
}
