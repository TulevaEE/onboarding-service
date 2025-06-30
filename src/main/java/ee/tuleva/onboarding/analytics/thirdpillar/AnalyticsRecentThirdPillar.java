package ee.tuleva.onboarding.analytics.thirdpillar;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Immutable
@Table(name = "v_third_pillar_api_weekly", schema = "analytics")
public class AnalyticsRecentThirdPillar implements Person {

  @Id private Long id;

  @ValidPersonalCode
  @Column(name = "personal_id")
  private String personalCode;

  @Column(name = "first_name")
  private String firstName;

  @Column(name = "last_name")
  private String lastName;

  @Column(name = "phone_no")
  private String phoneNo;

  private String email;

  private String country;

  @Column(name = "entry_type")
  private String entryType;

  private String language;

  @Column(name = "account_no")
  private String accountNo;

  @Column(name = "share_amount")
  private BigDecimal shareAmount;

  @Column(name = "latest_balance_date")
  private LocalDateTime latestBalanceDate;

  private String active;

  @Column(name = "death_date")
  private LocalDate deathDate;

  @Column(name = "reporting_date")
  private LocalDate reportingDate;

  @Column(name = "date_created")
  private LocalDateTime dateCreated;

  private String isin;

  @Column(name = "first_contribution_date")
  private LocalDate firstContributionDate;

  @Column(name = "first_identified_date")
  private LocalDate firstIdentifiedDate;

  @Column(name = "first_identified_by")
  private String firstIdentifiedBy;

  @Column(name = "sanctioned_blocked")
  private String sanctionedBlocked;

  @Column(name = "blocked_by")
  private String blockedBy;
}
