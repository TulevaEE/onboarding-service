package ee.tuleva.onboarding.epis.mandate;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.epis.mandate.MandateDto.MandateFundsTransferExchangeDTO;
import ee.tuleva.onboarding.mandate.ApplicationType;
import ee.tuleva.onboarding.mandate.application.ApplicationSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApplicationSnapshotMapperTest {

  private static final Instant DATE = Instant.parse("2021-03-09T10:00:00Z");

  @Test
  void mapsTransferApplicationWithPik() {
    ApplicationDTO dto =
        ApplicationDTO.builder()
            .date(DATE)
            .id(123L)
            .documentNumber("DOC-1")
            .status(ApplicationStatus.PENDING)
            .sourceFundIsin("EE3600109435")
            .fundTransferExchanges(
                List.of(
                    new MandateFundsTransferExchangeDTO(
                        "processId",
                        BigDecimal.valueOf(1.5),
                        "EE3600109435",
                        null,
                        "EE471000001020145685")))
            .type(ApplicationType.TRANSFER)
            .build();

    ApplicationSnapshot expected =
        ApplicationSnapshot.builder()
            .date(DATE)
            .id(123L)
            .documentNumber("DOC-1")
            .status(ee.tuleva.onboarding.mandate.application.ApplicationStatus.PENDING)
            .sourceFundIsin("EE3600109435")
            .fundTransferExchanges(
                List.of(
                    new ApplicationSnapshot.FundTransfer(
                        null, "EE471000001020145685", BigDecimal.valueOf(1.5))))
            .type(ApplicationType.TRANSFER)
            .build();

    assertThat(dto.toSnapshot()).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void mapsWithdrawalApplication() {
    ApplicationDTO dto =
        ApplicationDTO.builder()
            .date(DATE)
            .id(124L)
            .status(ApplicationStatus.PENDING)
            .type(ApplicationType.WITHDRAWAL)
            .bankAccount("EE471000001020145685")
            .build();

    ApplicationSnapshot expected =
        ApplicationSnapshot.builder()
            .date(DATE)
            .id(124L)
            .status(ee.tuleva.onboarding.mandate.application.ApplicationStatus.PENDING)
            .type(ApplicationType.WITHDRAWAL)
            .bankAccount("EE471000001020145685")
            .fundTransferExchanges(List.of())
            .build();

    assertThat(dto.toSnapshot()).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void mapsFundPensionOpeningApplication() {
    ApplicationDTO dto =
        ApplicationDTO.builder()
            .date(DATE)
            .id(125L)
            .status(ApplicationStatus.COMPLETE)
            .type(ApplicationType.FUND_PENSION_OPENING)
            .bankAccount("EE471000001020145685")
            .fundPensionDetails(new ApplicationDTO.FundPensionDetails(20, 12))
            .build();

    ApplicationSnapshot expected =
        ApplicationSnapshot.builder()
            .date(DATE)
            .id(125L)
            .status(ee.tuleva.onboarding.mandate.application.ApplicationStatus.COMPLETE)
            .type(ApplicationType.FUND_PENSION_OPENING)
            .bankAccount("EE471000001020145685")
            .fundTransferExchanges(List.of())
            .fundPensionDetails(new ApplicationSnapshot.FundPensionDetails(20, 12))
            .build();

    assertThat(dto.toSnapshot()).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void mapsPaymentRateApplication() {
    ApplicationDTO dto =
        ApplicationDTO.builder()
            .date(DATE)
            .id(126L)
            .status(ApplicationStatus.PENDING)
            .type(ApplicationType.PAYMENT_RATE)
            .paymentRate(BigDecimal.valueOf(6))
            .build();

    ApplicationSnapshot expected =
        ApplicationSnapshot.builder()
            .date(DATE)
            .id(126L)
            .status(ee.tuleva.onboarding.mandate.application.ApplicationStatus.PENDING)
            .type(ApplicationType.PAYMENT_RATE)
            .paymentRate(BigDecimal.valueOf(6))
            .fundTransferExchanges(List.of())
            .build();

    assertThat(dto.toSnapshot()).usingRecursiveComparison().isEqualTo(expected);
  }
}
