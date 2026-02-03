package ee.tuleva.onboarding.fund;

import static jakarta.persistence.EnumType.STRING;

import ee.tuleva.onboarding.fund.manager.FundManager;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Data
@Builder
@Entity
@Table(name = "fund")
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class Fund implements Comparable<Fund> {
  private static final List<String> EXIT_RESTRICTED_FUND_ISINS =
      List.of("EE3600109484", "EE3600001749", "EE3600001731");

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne private FundManager fundManager;

  @NotBlank private String isin;

  @NotBlank private String nameEstonian;

  @NotBlank private String nameEnglish;

  @NotNull private String shortName;

  @Min(2)
  @Max(3)
  private Integer pillar;

  @NotNull private BigDecimal managementFeeRate;

  @NotNull private BigDecimal equityShare;

  @NotNull private BigDecimal ongoingChargesFigure;

  @NotNull
  @Enumerated(STRING)
  private FundStatus status;

  private LocalDate inceptionDate;

  public enum FundStatus {
    ACTIVE, // Aktiivne
    LIQUIDATED, // Likvideeritud
    SUSPENDED, // Peatatud
    CONTRIBUTIONS_FORBIDDEN, // Sissemaksed keelatud
    PAYOUTS_FORBIDDEN // Väljamaksed keelatud
  }

  public String getName(Locale locale) {
    return Locale.ENGLISH.getLanguage().equals(locale.getLanguage()) ? nameEnglish : nameEstonian;
  }

  public boolean isOwnFund() {
    return fundManager.isTuleva();
  }

  public boolean isExitRestricted() {
    return EXIT_RESTRICTED_FUND_ISINS.contains(isin)
        || StringUtils.containsIgnoreCase(nameEstonian, "väljumine piiratud");
  }

  @Override
  public int compareTo(Fund other) {
    return nameEstonian.compareTo(other.nameEstonian);
  }
}
