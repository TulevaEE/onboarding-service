package ee.tuleva.onboarding.mandate;

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
    private Long id;

    @ManyToOne
    @JoinColumn(name = "mandate_id", nullable = false)
    private Mandate mandate;

    @NotBlank
    private String sourceFundIsin;

    @NotNull
    @Min(0)
    @Max(100)
    private Integer percent;

    @NotNull
    private String targetFundIsin;

}
