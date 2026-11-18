package ee.tuleva.onboarding.party;

import static lombok.AccessLevel.PROTECTED;

import ee.tuleva.onboarding.personalcode.PersonalCode;
import ee.tuleva.onboarding.populationregister.Guardian;
import ee.tuleva.onboarding.populationregister.PopulationRegisterResult;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "guardian_lookup")
@Getter
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
@ToString
public class GuardianLookup {

  private static final int AGE_OF_MAJORITY = 18;

  @Id private String childPersonalCode;

  @Column(nullable = false)
  private LocalDate validUntil;

  @Column(nullable = false)
  private Instant lookedUpAt;

  @Column(nullable = false)
  private UUID messageId;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "guardian_lookup_guardian",
      joinColumns = @JoinColumn(name = "child_personal_code"))
  private Set<LookedUpGuardian> guardians;

  int guardianCount() {
    return (int) guardians.stream().map(LookedUpGuardian::guardianPersonalCode).distinct().count();
  }

  static GuardianLookup of(
      String childPersonalCode, PopulationRegisterResult<List<Guardian>> answer, Instant at) {
    return new GuardianLookup(
        childPersonalCode,
        PersonalCode.getDateOfBirth(childPersonalCode).plusYears(AGE_OF_MAJORITY),
        at,
        answer.messageId(),
        answer.data().stream()
            .filter(Guardian::holdsCustody)
            .map(guardian -> new LookedUpGuardian(guardian.personalCode(), guardian.custodyType()))
            .collect(Collectors.toUnmodifiableSet()));
  }
}
