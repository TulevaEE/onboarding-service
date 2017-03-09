package ee.tuleva.onboarding.mandate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.*;
import org.hibernate.validator.constraints.NotBlank;

import javax.persistence.*;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@Getter
@Entity
@Table(name = "fund_transfer_exchange")
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    @NotNull
    @Min(0)
    @Max(1)
    @JsonView(MandateView.Default.class)
    private BigDecimal amount;
    @NotNull
    @JsonView(MandateView.Default.class)
    private String targetFundIsin;

}
