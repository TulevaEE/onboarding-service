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

  @Data
  @Builder
  public static class Exchange {

    private FundDto sourceFund;
    private FundDto targetFund;
    private String targetPik;
    private BigDecimal amount;

    @JsonIgnore
    public Integer getPillar() {
      Integer sourcePillar = sourceFund.getPillar();
      Integer targetPillar = getTargetPillar();

      if (sourcePillar.equals(targetPillar)) {
        return sourcePillar;
      }
      throw new IllegalStateException("Transfer between different pillar funds");
    }

    private Integer getTargetPillar() {
      return targetPik != null ? 2 : targetFund.getPillar();
    }
  }
}
