package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.RESERVED;
import static jakarta.persistence.EnumType.STRING;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.time.ClockHolder;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
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

  // RedemptionController returns this entity to the customer, and RahaPTS forbids telling the
  // customer about an AML suspicion, so the review and hold columns never leave the server.
  @JsonIgnore @Nullable private String reviewedBy;

  @JsonIgnore @Nullable private String reviewReason;

  @JsonIgnore @Nullable private Instant reviewedAt;

  @JsonIgnore @Nullable private String holdReason;

  @JsonIgnore @Nullable private Instant holdAt;

  @JsonIgnore @Nullable private String heldBy;

  @JsonIgnore @Nullable private Instant holdNotifiedAt;

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

  public boolean hasActiveHold() {
    return holdReason != null && reviewedAt == null;
  }
}
