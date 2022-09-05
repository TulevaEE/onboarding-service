package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.mandate.application.ApplicationType.TRANSFER;

import ee.tuleva.onboarding.fund.ApiFundResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
public class TransferApplicationDetails implements ApplicationDetails {

  private final ApiFundResponse sourceFund;
  private final Instant cancellationDeadline;
  private final LocalDate fulfillmentDate;
  @Singular private List<Exchange> exchanges;
  @Builder.Default private ApplicationType type = TRANSFER;

  public TransferApplicationDetails(
      ApiFundResponse sourceFund,
      Instant cancellationDeadline,
      LocalDate fulfillmentDate,
      List<Exchange> exchanges,
      ApplicationType type) {
    validate(type);
    this.sourceFund = sourceFund;
    this.cancellationDeadline = cancellationDeadline;
    this.fulfillmentDate = fulfillmentDate;
    this.exchanges = exchanges;
    this.type = type;
  }

  private void validate(ApplicationType type) {
    if (type != TRANSFER) {
      throw new IllegalArgumentException("Invalid ApplicationType: type=" + type);
    }
  }

  @Override
  public Integer getPillar() {
    Integer sourcePillar = sourceFund.getPillar();
    if (exchanges.stream().allMatch(exchange -> sourcePillar.equals(exchange.getPillar()))) {
      return sourcePillar;
    }
    throw new IllegalStateException("Transfer between different pillar funds");
  }
}
