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
public class Fund implements Comparable<Fund> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private FundManager fundManager;

    @NotBlank
    private String isin;

    @NotBlank
    private String nameEstonian;

    @NotBlank
    private String nameEnglish;

    @NotNull
    private String shortName;

    @NotNull
    private Integer pillar;

    @NotNull
    private BigDecimal managementFeeRate;

    @NotNull
    private BigDecimal equityShare;

    @NotNull
    private BigDecimal ongoingChargesFigure;

    @NotNull
    @Enumerated(STRING)
    private FundStatus status;

    public enum FundStatus {
        ACTIVE, // Aktiivne
        LIQUIDATED, // Likvideeritud
        SUSPENDED, // Peatatud
        CONTRIBUTIONS_FORBIDDEN, // Sissemaksed keelatud
        PAYOUTS_FORBIDDEN // Väljamaksed keelatud
    }

    public String getName(String language) {
        return "en".equalsIgnoreCase(language) ? nameEnglish : nameEstonian;
    }

    @Override
    public int compareTo(Fund other) {
        return nameEstonian.compareTo(other.nameEstonian);
    }

}

