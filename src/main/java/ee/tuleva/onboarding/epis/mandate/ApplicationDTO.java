package ee.tuleva.onboarding.epis.mandate;

import ee.tuleva.onboarding.mandate.application.ApplicationType;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ApplicationDTO implements Serializable {

  private String currency;
  private Instant date;
  private Long id;
  private String documentNumber;
  private BigDecimal amount;
  private ApplicationStatus status;
  private String sourceFundIsin;
  private String targetFundIsin;
  private ApplicationType type;
}
