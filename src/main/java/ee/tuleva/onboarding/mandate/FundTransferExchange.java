package ee.tuleva.onboarding.mandate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import java.math.BigDecimal;
import javax.persistence.*;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import lombok.*;

@Data
@Getter
@Entity
@Table(name = "fund_transfer_exchange")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"mandate"})
public class FundTransferExchange {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @JsonView(MandateView.Default.class)
  private Long id;

  @JsonIgnore
  @ManyToOne
  @JoinColumn(name = "mandate_id", nullable = false)
  private Mandate mandate;

  @NotBlank
  @JsonView(MandateView.Default.class)
  private String sourceFundIsin;

  /**
   * 2nd pillar: Fraction of units (i.e. min 0, max 1) 3rd pillar: Number of units (i.e. min 0, max
   * number of units you have)
   */
  @Min(0)
  @JsonView(MandateView.Default.class)
  private BigDecimal amount;

  @JsonView(MandateView.Default.class)
  private String targetFundIsin;
}
