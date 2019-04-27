package ee.tuleva.onboarding.fund;

import ee.tuleva.onboarding.fund.manager.FundManager;
import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@Builder
@Entity
@Table(name = "fund")
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class Fund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private FundManager fundManager;

    @NotBlank
    private String isin;

    @NotBlank
    private String name;

    @NotNull
    private BigDecimal managementFeeRate;

}
