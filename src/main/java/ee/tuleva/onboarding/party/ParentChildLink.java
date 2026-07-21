package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.PENDING_KYC;
import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.UUID;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

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

  @NotNull
  @Builder.Default
  @Enumerated(STRING)
  @Column(columnDefinition = "parent_child_link_status", nullable = false)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private ParentChildLinkStatus status = ACTIVE;

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
    return status == PENDING_KYC;
  }

  void activate() {
    status = ACTIVE;
  }
}
