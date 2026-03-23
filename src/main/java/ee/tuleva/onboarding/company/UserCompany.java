package ee.tuleva.onboarding.company;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.UUID;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "user_company")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class UserCompany {

  @Id
  @GeneratedValue(strategy = UUID)
  private UUID id;

  @NotNull
  @Column(nullable = false)
  private Long userId;

  @NotNull
  @Column(nullable = false)
  private UUID companyId;

  @NotNull
  @Enumerated(STRING)
  @Column(nullable = false)
  private RelationshipType relationshipType;

  @Column(nullable = false, updatable = false)
  private Instant createdDate;

  @PrePersist
  void prePersist() {
    if (createdDate == null) {
      createdDate = Instant.now();
    }
  }
}
