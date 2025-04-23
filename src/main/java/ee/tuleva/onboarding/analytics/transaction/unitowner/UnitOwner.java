package ee.tuleva.onboarding.analytics.transaction.unitowner;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(
    name = "unit_owner",
    indexes = {
      @Index(name = "idx_unit_owner_personal_id", columnList = "personal_id"),
      @Index(name = "idx_unit_owner_snapshot_date", columnList = "snapshot_date"),
      @Index(name = "idx_unit_owner_fund_manager", columnList = "fund_manager")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_unit_owner_personal_id_snapshot_date",
          columnNames = {"personal_id", "snapshot_date"})
    })
public class UnitOwner {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, name = "personal_id")
  private String personalId;

  @Column(name = "snapshot_date", nullable = false)
  private LocalDate snapshotDate;

  @Column(name = "first_name")
  private String firstName;

  @Column(name = "last_name")
  private String lastName;

  private String phone;
  private String email;
  private String country;

  @Column(name = "language_preference")
  private String languagePreference;

  @Column(name = "pension_account")
  private String pensionAccount;

  @Column(name = "death_date")
  private LocalDate deathDate;

  @Column(name = "fund_manager")
  private String fundManager;

  @Column(name = "p2_choice")
  private String p2choice;

  @Column(name = "p2_choice_method")
  private String p2choiceMethod;

  @Column(name = "p2_choice_date")
  private LocalDate p2choiceDate;

  @Column(name = "p2_rava_date")
  private LocalDate p2ravaDate;

  @Column(name = "p2_rava_status")
  private String p2ravaStatus;

  @Column(name = "p2_mmte_date")
  private LocalDate p2mmteDate;

  @Column(name = "p2_mmte_status")
  private String p2mmteStatus;

  @Column(name = "p2_rate")
  private Integer p2rate;

  @Column(name = "p2_next_rate")
  private Integer p2nextRate;

  @Column(name = "p2_next_rate_date")
  private LocalDate p2nextRateDate;

  @Column(name = "p2_ykva_date")
  private LocalDate p2ykvaDate;

  @Column(name = "p2_plav_date")
  private LocalDate p2plavDate;

  @Column(name = "p2_fpaa_date")
  private LocalDate p2fpaaDate;

  @Column(name = "p2_duty_start")
  private LocalDate p2dutyStart;

  @Column(name = "p2_duty_end")
  private LocalDate p2dutyEnd;

  @Column(name = "p3_identification_date")
  private LocalDate p3identificationDate;

  @Column(name = "p3_identifier")
  private String p3identifier;

  @Column(name = "p3_block_flag")
  private String p3blockFlag;

  @Column(name = "p3_blocker")
  private String p3blocker;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "unit_owner_balance", joinColumns = @JoinColumn(name = "unit_owner_id"))
  @AttributeOverrides({
    @AttributeOverride(name = "securityShortName", column = @Column(name = "security_short_name")),
    @AttributeOverride(name = "securityName", column = @Column(name = "security_name")),
    @AttributeOverride(name = "type", column = @Column(name = "balance_type")),
    @AttributeOverride(name = "amount", column = @Column(name = "balance_amount")),
    @AttributeOverride(name = "startDate", column = @Column(name = "start_date")),
    @AttributeOverride(name = "lastUpdated", column = @Column(name = "last_updated"))
  })
  private List<UnitOwnerBalanceEmbeddable> balances;

  @Column(name = "date_created", nullable = false)
  private LocalDateTime dateCreated;
}
