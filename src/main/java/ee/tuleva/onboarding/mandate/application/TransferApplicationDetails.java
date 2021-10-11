package ee.tuleva.onboarding.mandate.application;

import ee.tuleva.onboarding.fund.response.FundDto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
public class TransferApplicationDetails implements ApplicationDetails {

  private final FundDto sourceFund;
  private final Instant cancellationDeadline;
  private final LocalDate fulfillmentDate;
  @Singular private List<Exchange> exchanges;

  @Override
  public Integer getPillar() {
    Integer sourcePillar = sourceFund.getPillar();
    if (exchanges.stream().allMatch(exchange -> sourcePillar.equals(exchange.getPillar()))) {
      return sourcePillar;
    }
    throw new IllegalStateException("Transfer between different pillar funds");
  }
}
