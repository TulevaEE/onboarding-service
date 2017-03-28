package ee.tuleva.onboarding.mandate.statistics;

import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;

import javax.persistence.*;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Entity
@Table(name = "fund_transfer_statistics")
@Data
@Builder
public class FundTransferStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank
    private String isin;
    @NotNull
    @Min(0)
    private BigDecimal value;
    @NotNull
    @Min(0)
    @Max(1)
    private BigDecimal amount;

}
