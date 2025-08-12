package ee.tuleva.onboarding.listing;

import static ee.tuleva.onboarding.listing.Listing.State.ACTIVE;
import static ee.tuleva.onboarding.listing.Listing.State.CANCELLED;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.time.ClockHolder;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "listing")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class Listing {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  @NotNull
  @Column(nullable = false, updatable = false)
  private Long memberId;

  @Enumerated(STRING)
  @NotNull
  @Column(nullable = false, updatable = false)
  private ListingType type;

  @NotNull
  @Column(nullable = false, updatable = false)
  private BigDecimal units;

  @NotNull
  @Column(nullable = false, updatable = false)
  private BigDecimal totalPrice;

  @Enumerated(STRING)
  @NotNull
  @Column(nullable = false, updatable = false)
  private Currency currency;

  @Column(nullable = false)
  @NotNull
  private String language;

  /** ACTIVE → IN_PROGRESS → CANCELLED / COMPLETED */
  @Enumerated(STRING)
  @NotNull
  private State state;

  @Column(nullable = false, updatable = false)
  @NotNull
  private Instant expiryTime;

  @Column(nullable = false, updatable = false)
  @NotNull
  private Instant createdTime;

  private Instant cancelledTime;

  private Instant completedTime;

  @PrePersist
  void prePersist() {
    createdTime = ClockHolder.clock().instant();
  }

  public void cancel() {
    if (state != ACTIVE || ClockHolder.clock().instant().isAfter(expiryTime)) {
      throw new IllegalStateException("You can only cancel listings that are active.");
    }
    state = CANCELLED;
    cancelledTime = ClockHolder.clock().instant();
  }

  public enum State {
    ACTIVE,
    IN_PROGRESS,
    CANCELLED,
    COMPLETED
  }
}
