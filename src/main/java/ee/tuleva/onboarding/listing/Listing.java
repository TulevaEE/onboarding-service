package ee.tuleva.onboarding.listing;

import static ee.tuleva.onboarding.listing.Listing.State.ACTIVE;
import static ee.tuleva.onboarding.listing.Listing.State.CANCELLED;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.time.ClockHolder;
import jakarta.persistence.*;
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

  @Column(nullable = false)
  private Long memberId;

  @Enumerated(STRING)
  @Column(nullable = false, length = 4)
  private ListingType type;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal units;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal pricePerUnit;

  @Enumerated(STRING)
  @Column(nullable = false, length = 3)
  private Currency currency;

  /** ACTIVE → IN_PROGRESS → CANCELLED / COMPLETED */
  @Enumerated(STRING)
  private State state;

  @Column(nullable = false)
  private Instant expiryTime;

  @Column(nullable = false, updatable = false)
  private Instant createdTime;

  @Column private Instant cancelledTime;

  @Column private Instant completedTime;

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
