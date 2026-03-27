package ee.tuleva.onboarding.company;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.UUID;

import ee.tuleva.onboarding.party.Party;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "company_party")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CompanyParty {

  @Id
  @GeneratedValue(strategy = UUID)
  private UUID id;

  @NotNull
  @Column(nullable = false)
  private String partyCode;

  @NotNull
  @Enumerated(STRING)
  @Column(nullable = false)
  private Party.Type partyType;

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
