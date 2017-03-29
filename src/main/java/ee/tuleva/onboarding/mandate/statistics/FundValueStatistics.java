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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "fund_value_statistics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundValueStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String isin;

    @Min(0)
    @NotNull
    private BigDecimal value;

    @NotNull
    private UUID identifier;

    @NotNull
    private Instant createdDate;

    @PrePersist
    protected void onCreate() {
        createdDate = Instant.now().truncatedTo(ChronoUnit.DAYS);
    }

}
