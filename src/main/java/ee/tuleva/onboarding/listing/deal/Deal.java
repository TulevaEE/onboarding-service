package ee.tuleva.onboarding.listing.deal;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;

import ee.tuleva.onboarding.listing.Listing;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "deal")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Deal {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  @OneToOne
  @JoinColumn(name = "listing_id")
  @NotNull
  private Listing listing;

  @Column(nullable = false)
  @NotNull
  private Long memberId;

  /** IN_PROGRESS → BUYER_CONFIRMED / SELLER_CONFIRMED → COMPLETED */
  @Enumerated(STRING)
  @NotNull
  @Column(nullable = false, updatable = false)
  private State state;

  @Column(nullable = false, updatable = false)
  private Instant createdTime;

  private Instant buyerConfirmedTime;
  private Instant sellerConfirmedTime;

  public enum State {
    IN_PROGRESS,
    BUYER_CONFIRMED,
    SELLER_CONFIRMED,
    COMPLETED
  }
}
