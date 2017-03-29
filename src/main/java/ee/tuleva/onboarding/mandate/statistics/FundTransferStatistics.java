package ee.tuleva.onboarding.mandate.statistics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;

import javax.persistence.*;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Entity
@Table(name = "fund_transfer_statistics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private BigDecimal transferred;

}
