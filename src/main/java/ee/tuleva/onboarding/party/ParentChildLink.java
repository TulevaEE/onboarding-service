package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.UUID;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "parent_child_link")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ParentChildLink {

  @Id
  @GeneratedValue(strategy = UUID)
  private UUID id;

  @NotNull
  @Column(nullable = false)
  private String parentPersonalCode;

  @NotNull
  @Column(nullable = false)
  private String childPersonalCode;

  @NotNull
  @Enumerated(STRING)
  @Column(nullable = false)
  private RepresentationType relationshipType;

  @NotNull
  @Column(nullable = false)
  private LocalDate validUntil;

  // ACTIVE authorizes representation; PENDING_KYC grants no access until the co-parent completes
  // their own onboarding/KYC. Defaulted to ACTIVE so ParentChildLink.builder() never yields a null
  // status against the non-null column (pending links are created via a dedicated native insert).
  @NotNull
  @Builder.Default
  @Enumerated(STRING)
  @Column(nullable = false)
  private ParentChildLinkStatus status = ParentChildLinkStatus.ACTIVE;

  private Instant suspendedAt;

  @Column(nullable = false, updatable = false)
  private Instant createdDate;

  @PrePersist
  void prePersist() {
    if (createdDate == null) {
      createdDate = clock().instant();
    }
  }

  public boolean isSuspended() {
    return suspendedAt != null;
  }

  public boolean isPending() {
    return status == ParentChildLinkStatus.PENDING_KYC;
  }

  public boolean isActive() {
    return status == ParentChildLinkStatus.ACTIVE;
  }

  // Activation is the real "co-parent added" moment: flips a PENDING_KYC link to ACTIVE once the
  // co-parent has completed their own onboarding/KYC.
  void activate() {
    this.status = ParentChildLinkStatus.ACTIVE;
  }
}
