package ee.tuleva.onboarding.listing;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;

import ee.tuleva.onboarding.currency.Currency;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

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

  @Column(nullable = false)
  private Instant expiryTime;

  @Column(nullable = false, updatable = false)
  private Instant createdTime;

  @PrePersist
  void prePersist() {
    createdTime = Instant.now();
  }
}
