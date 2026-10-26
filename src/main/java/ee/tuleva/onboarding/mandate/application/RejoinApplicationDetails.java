package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.applicationtype.ApplicationType.REJOIN;

import ee.tuleva.onboarding.applicationtype.ApplicationType;
import java.time.Instant;
import java.time.LocalDate;

public record RejoinApplicationDetails(Instant cancellationDeadline, LocalDate fulfillmentDate)
    implements SecondPillarApplicationDetails {

  @Override
  public ApplicationType getType() {
    return REJOIN;
  }
}
