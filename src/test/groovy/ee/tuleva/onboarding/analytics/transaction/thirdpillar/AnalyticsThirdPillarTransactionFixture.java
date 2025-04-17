package ee.tuleva.onboarding.analytics.transaction.thirdpillar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class AnalyticsThirdPillarTransactionFixture {

  public static final String CREATE_ANALYTICS_SCHEMA = "CREATE SCHEMA IF NOT EXISTS analytics;";

  public static final String CREATE_THIRD_PILLAR_TABLE =
      """
      CREATE TABLE IF NOT EXISTS analytics.third_pillar_transactions (
        id                SERIAL PRIMARY KEY,
        reporting_date    DATE            NOT NULL,
        full_name         VARCHAR(255)    NOT NULL,
        personal_id       VARCHAR(255)    NOT NULL,
        account_no        VARCHAR(255)    NOT NULL,
        country           VARCHAR(255),
        transaction_type  VARCHAR(255)    NOT NULL,
        transaction_source VARCHAR(255),
        application_type  VARCHAR(255),
        share_amount      NUMERIC         NOT NULL,
        share_price       NUMERIC         NOT NULL,
        nav               NUMERIC         NOT NULL,
        transaction_value NUMERIC         NOT NULL,
        service_fee       NUMERIC,
        date_created      TIMESTAMP       NOT NULL DEFAULT now(),
        fund_manager      VARCHAR(255),
        fund             VARCHAR(255),
        purpose_code      INTEGER,
        counterparty_name VARCHAR(255),
        counterparty_code VARCHAR(255),
        counterparty_bank_account VARCHAR(255),
        counterparty_bank VARCHAR(255),
        counterparty_bic  VARCHAR(255)
      );
      """;

  public static final String TRUNCATE_THIRD_PILLAR_TABLE =
      "TRUNCATE TABLE analytics.third_pillar_transactions;";

  public static AnalyticsThirdPillarTransaction exampleTransaction() {
    return exampleTransactionBuilder().build();
  }

  public static AnalyticsThirdPillarTransaction.AnalyticsThirdPillarTransactionBuilder
      exampleTransactionBuilder() {
    return AnalyticsThirdPillarTransaction.builder()
        .reportingDate(LocalDate.of(2023, 1, 1))
        .fullName("John Doe")
        .personalId("12345678901")
        .accountNo("EE123456")
        .country("EE")
        .transactionType("Contribution")
        .transactionSource("someSource")
        .applicationType("XYZ")
        .shareAmount(BigDecimal.valueOf(10))
        .sharePrice(BigDecimal.valueOf(10))
        .nav(BigDecimal.valueOf(100))
        .transactionValue(BigDecimal.valueOf(100.00))
        .serviceFee(BigDecimal.valueOf(1.00))
        .dateCreated(LocalDateTime.now())
        .fundManager("Tuleva")
        .fund("Tuleva World")
        .purposeCode(500)
        .counterpartyName("SomeBank")
        .counterpartyCode("1111")
        .counterpartyBankAccount("EE000111")
        .counterpartyBank("SWED")
        .counterpartyBic("SWED1234");
  }

  public static AnalyticsThirdPillarTransaction anotherExampleTransaction() {
    return anotherExampleTransactionBuilder().build();
  }

  public static AnalyticsThirdPillarTransaction.AnalyticsThirdPillarTransactionBuilder
      anotherExampleTransactionBuilder() {
    return AnalyticsThirdPillarTransaction.builder()
        .reportingDate(LocalDate.of(2023, 2, 15))
        .fullName("Jane Smith")
        .personalId("22222222222")
        .accountNo("EE0022")
        .country("EE")
        .transactionType("REDEMPTION")
        .transactionSource("someOtherSource")
        .applicationType("XYZ")
        .shareAmount(BigDecimal.TEN)
        .sharePrice(BigDecimal.TEN)
        .nav(BigDecimal.valueOf(100))
        .transactionValue(BigDecimal.valueOf(1000))
        .serviceFee(BigDecimal.valueOf(5.00))
        .dateCreated(LocalDateTime.now())
        .fundManager("Tuleva")
        .fund("Tuleva World")
        .purposeCode(501)
        .counterpartyName("OtherBank")
        .counterpartyCode("2222")
        .counterpartyBankAccount("EE000222")
        .counterpartyBank("SEB")
        .counterpartyBic("SEB1234");
  }
}
