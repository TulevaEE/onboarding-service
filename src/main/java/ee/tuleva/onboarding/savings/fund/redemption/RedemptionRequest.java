package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.RESERVED;
import static jakarta.persistence.EnumType.STRING;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.time.ClockHolder;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;

@Data
@Builder
@Entity
@Table(name = "redemption_request")
@NoArgsConstructor
@AllArgsConstructor
public class RedemptionRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(STRING)
  @Column(name = "party_type", nullable = false)
  @NonNull
  private PartyId.Type partyType;

  @Column(name = "party_code", nullable = false)
  @NonNull
  private String partyCode;

  @Column(nullable = false, precision = 15, scale = 5)
  private BigDecimal fundUnits;

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal requestedAmount;

  @Column(nullable = false, length = 34)
  private String customerIban;

  @Enumerated(STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private Status status = RESERVED;

  @Column(nullable = false)
  private Instant requestedAt;

  @Nullable private Instant cancelledAt;

  @Nullable private Instant processedAt;

  @Nullable
  @Column(precision = 15, scale = 2)
  private BigDecimal cashAmount;

  @Nullable
  @Column(precision = 15, scale = 5)
  private BigDecimal navPerUnit;

  @Nullable private String errorReason;

  @Nullable private Instant verificationAttemptedAt;

  @JsonIgnore @Nullable private String reviewedBy;

  @JsonIgnore @Nullable private String reviewReason;

  @JsonIgnore @Nullable private Instant reviewedAt;

  @JsonIgnore
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "redemption_hold_reason",
      joinColumns =
          @JoinColumn(
              name = "redemption_request_id",
              foreignKey = @ForeignKey(name = "fk_redemption_hold_reason_request")))
  @Column(name = "reason", nullable = false)
  @Enumerated(STRING)
  @Builder.Default
  private Set<RedemptionHoldReason> holdReasons = EnumSet.noneOf(RedemptionHoldReason.class);

  @JsonIgnore @Nullable private String holdComment;

  @JsonIgnore @Nullable private Instant holdAt;

  @JsonIgnore @Nullable private String heldBy;

  @JsonIgnore @Nullable private Instant holdNotifiedAt;

  @JsonIgnore @Nullable private Instant holdReleasedAt;

  @JsonIgnore @Nullable private Instant requeuedAt;

  @JsonIgnore @Nullable private UUID batchId;

  @Column(nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    Instant now = ClockHolder.clock().instant();
    updatedAt = now;
    if (requestedAt == null) {
      requestedAt = now;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = ClockHolder.clock().instant();
  }

  public enum Status {
    RESERVED,
    FROZEN,
    VERIFIED,
    PAYOUT_HELD,
    REDEEMED,
    PROCESSED,
    CANCELLED,
    FAILED
  }

  public PartyId getPartyId() {
    return new PartyId(partyType, partyCode);
  }

  public Set<RedemptionHoldReason> getHoldReasons() {
    return holdReasons.isEmpty() ? Set.of() : EnumSet.copyOf(holdReasons);
  }

  public void setHoldReasons(Set<RedemptionHoldReason> reasons) {
    holdReasons =
        reasons.isEmpty() ? EnumSet.noneOf(RedemptionHoldReason.class) : EnumSet.copyOf(reasons);
  }

  public boolean hasActiveHold() {
    return !holdReasons.isEmpty() && holdReleasedAt == null;
  }

  public boolean amountReconciles() {
    if (fundUnits == null || navPerUnit == null || cashAmount == null) {
      return false;
    }
    return expectedAmount().compareTo(cashAmount) == 0;
  }

  public BigDecimal expectedAmount() {
    return fundUnits.multiply(navPerUnit).setScale(2, RoundingMode.HALF_UP);
  }
}
