package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.pillar.Pillar.SECOND;
import static ee.tuleva.onboarding.pillar.Pillar.THIRD;

import ee.tuleva.onboarding.epis.mandate.ApplicationDTO.FundPensionDetails;
import ee.tuleva.onboarding.pillar.Pillar;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;

@Data
public class FundPensionOpeningApplicationDetails implements ApplicationDetails {

  private final String depositAccountIBAN;
  private final Instant cancellationDeadline;
  private final LocalDate fulfillmentDate;
  private final ApplicationType type;
  private final FundPensionDetails fundPensionDetails;

  public FundPensionOpeningApplicationDetails(
      String depositAccountIBAN,
      Instant cancellationDeadline,
      LocalDate fulfillmentDate,
      ApplicationType type,
      FundPensionDetails fundPensionDetails) {
    validate(type);
    this.depositAccountIBAN = depositAccountIBAN;
    this.cancellationDeadline = cancellationDeadline;
    this.fulfillmentDate = fulfillmentDate;
    this.type = type;
    this.fundPensionDetails = fundPensionDetails;
  }

  private Pillar mapApplicationTypeToPillar() {
    return switch (type) {
      case FUND_PENSION_OPENING -> SECOND;
      case FUND_PENSION_OPENING_THIRD_PILLAR -> THIRD;
      default -> throw new IllegalStateException("Invalid ApplicationType: type=" + type);
    };
  }

  private void validate(ApplicationType type) {
    if (type == null || !type.isFundPensionOpening()) {
      throw new IllegalArgumentException("Invalid ApplicationType: type=" + type);
    }
  }

  @Override
  public Integer getPillar() {
    return mapApplicationTypeToPillar().toInt();
  }
}
