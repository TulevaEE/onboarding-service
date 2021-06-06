package ee.tuleva.onboarding.mandate.application;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.fund.response.FundDto;
import java.math.BigDecimal;
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
  @Singular private List<Exchange> exchanges;
  private final Instant cancellationDeadline;
  private final LocalDate fulfillmentDate;

  @Data
  @Builder
  public static class Exchange {

    private FundDto sourceFund;
    private FundDto targetFund;
    private BigDecimal amount;

    @JsonIgnore
    public Integer getPillar() {
      if (sourceFund.getPillar().equals(targetFund.getPillar())) {
        return sourceFund.getPillar();
      }
      throw new IllegalStateException("Transfer between different pillar funds");
    }
  }

  @Override
  public Integer getPillar() {
    if (exchanges.stream()
        .allMatch(exchange -> sourceFund.getPillar().equals(exchange.targetFund.getPillar()))) {
      return sourceFund.getPillar();
    }
    throw new IllegalStateException("Transfer between different pillar funds");
  }
}
