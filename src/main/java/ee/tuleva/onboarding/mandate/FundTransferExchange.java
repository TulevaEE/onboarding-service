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
@Table(name = "mandate")
public class FundTransferExchange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    Mandate mandate;

    @NotBlank
    String sourceFundIsin;
    @NotNull
    @Min(0)
    @Max(100)
    Integer percent;
    @NotNull
    String targetFundIsin;

    @Builder
    FundTransferExchange(String sourceFundIsin, String targetFundIsin, Integer percent) {
        this.sourceFundIsin = sourceFundIsin;
        this.targetFundIsin = targetFundIsin;
        this.percent = percent;
    }

}
