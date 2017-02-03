package ee.tuleva.onboarding.mandate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;

import javax.persistence.*;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
@Entity
@Table(name = "fund_transfer_exchange")
@Builder
public class FundTransferExchange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(MandateView.Default.class)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "mandate_id", nullable = false)
    Mandate mandate;

    @NotBlank
    @JsonView(MandateView.Default.class)
    String sourceFundIsin;
    @NotNull
    @Min(0)
    @Max(100)
    @JsonView(MandateView.Default.class)
    Integer percent;
    @NotNull
    @JsonView(MandateView.Default.class)
    String targetFundIsin;

}
