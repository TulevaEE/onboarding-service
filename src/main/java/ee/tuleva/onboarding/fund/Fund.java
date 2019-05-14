package ee.tuleva.onboarding.fund;

import ee.tuleva.onboarding.fund.manager.FundManager;
import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

import static javax.persistence.EnumType.STRING;

@Data
@Builder
@Entity
@Table(name = "fund")
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class Fund {

    public enum FundStatus {
        ACTIVE, // Aktiivne
        LIQUIDATED, // Likvideeritud
        SUSPENDED, // Peatatud
        CONTRIBUTIONS_FORBIDDEN, // Sissemaksed keelatud
        PAYOUTS_FORBIDDEN // Väljamaksed keelatud
    }

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
    private Integer pillar;

    @NotNull
    private BigDecimal managementFeeRate;

    @NotNull
    private BigDecimal ongoingChargesFigure;

    @NotNull
    @Enumerated(value = STRING)
    private FundStatus status;
}
