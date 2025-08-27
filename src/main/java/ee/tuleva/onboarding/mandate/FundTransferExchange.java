package ee.tuleva.onboarding.mandate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

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
   * 2nd pillar: Fraction of bookValue (i.e. min 0, max 1) 3rd pillar: Number of bookValue (i.e. min
   * 0, max number of bookValue you have)
   */
  @Min(0)
  @JsonView(MandateView.Default.class)
  private BigDecimal amount;

  @JsonView(MandateView.Default.class)
  private String targetFundIsin;

  @JsonView(MandateView.Default.class)
  private String targetPik;
}
