package ee.tuleva.onboarding.analytics.transaction.thirdpillar;

import ee.tuleva.onboarding.epis.transaction.ThirdPillarTransactionDto;
import java.math.BigDecimal;
import java.time.LocalDate;

public class ThirdPillarTransactionFixture {

  public static final String JOHN_DOE_PERSONAL_ID = "12345678901";
  public static final String JANE_SMITH_PERSONAL_ID = "12345678902";

  public static ThirdPillarTransactionDto johnDoeTransaction() {
    return ThirdPillarTransactionDto.builder()
        .date(LocalDate.of(2023, 1, 15))
        .personName("John Doe")
        .personId(JOHN_DOE_PERSONAL_ID)
        .pensionAccount("EE123456")
        .transactionType("SUBSCRIPTION")
        .purpose("Contribution Q1")
        .applicationType("ONLINE")
        .unitAmount(BigDecimal.valueOf(10))
        .price(BigDecimal.valueOf(1.25))
        .nav(BigDecimal.valueOf(12.50))
        .amount(BigDecimal.valueOf(12.50))
        .serviceFee(BigDecimal.valueOf(0.10))
        .fundManager("Tuleva")
        .fund("Tuleva World Stocks")
        .purposeCode(501)
        .counterpartyName("Some Bank")
        .counterpartyCode("1111")
        .counterpartyBankAccount("EE000111")
        .counterpartyBank("SWED")
        .counterpartyBic("HABAEE2X")
        .build();
  }

  public static ThirdPillarTransactionDto janeSmithTransaction() {
    return ThirdPillarTransactionDto.builder()
        .date(LocalDate.of(2023, 1, 15))
        .personName("Jane Smith")
        .personId(JANE_SMITH_PERSONAL_ID)
        .pensionAccount("EE654321")
        .transactionType("SUBSCRIPTION")
        .purpose("Contribution Q1")
        .applicationType("ONLINE")
        .unitAmount(BigDecimal.valueOf(5))
        .price(BigDecimal.valueOf(2.00))
        .nav(BigDecimal.valueOf(10.00))
        .amount(BigDecimal.valueOf(10.00))
        .serviceFee(BigDecimal.valueOf(0.05))
        .fundManager("Tuleva")
        .fund("Tuleva World Stocks")
        .purposeCode(501)
        .counterpartyName("Other Bank")
        .counterpartyCode("2222")
        .counterpartyBankAccount("EE000222")
        .counterpartyBank("SEB")
        .counterpartyBic("EESEB2X")
        .build();
  }
}
