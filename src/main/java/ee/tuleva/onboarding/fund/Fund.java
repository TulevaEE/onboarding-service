package ee.tuleva.onboarding.fund;

import static javax.persistence.EnumType.STRING;

import ee.tuleva.onboarding.fund.manager.FundManager;
import java.math.BigDecimal;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@Builder
@Entity
@Table(name = "fund")
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class Fund implements Comparable<Fund> {
  private static final String EXIT_RESTRICTED_FUND_ISIN = "EE3600109484";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne private FundManager fundManager;

  @NotBlank private String isin;

  @NotBlank private String nameEstonian;

  @NotBlank private String nameEnglish;

  @NotNull private String shortName;

  @NotNull private Integer pillar;

  @NotNull private BigDecimal managementFeeRate;

  @NotNull private BigDecimal equityShare;

  @NotNull private BigDecimal ongoingChargesFigure;

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

  public boolean isConverted() {
    return fundManager.isTuleva();
  }

  public boolean isExitRestricted() {
    return EXIT_RESTRICTED_FUND_ISIN.equals(isin);
  }

  @Override
  public int compareTo(Fund other) {
    return nameEstonian.compareTo(other.nameEstonian);
  }
}
