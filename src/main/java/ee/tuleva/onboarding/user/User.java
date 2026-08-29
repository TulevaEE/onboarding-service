package ee.tuleva.onboarding.user;

import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static jakarta.persistence.CascadeType.*;
import static jakarta.persistence.GenerationType.*;
import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.party.Party;
import ee.tuleva.onboarding.user.exception.NotAMemberException;
import ee.tuleva.onboarding.user.member.Member;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;
import lombok.*;
import org.jspecify.annotations.Nullable;

@Data
@Builder
@Entity
@Table(name = "users")
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"member"})
@ToString(exclude = {"member"})
public class User implements Person, Emailable, Serializable, Party {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private @Nullable Long id;

  @OneToOne(cascade = ALL, mappedBy = "user")
  Member member;

  @ValidPersonalCode private String personalCode;

  @Email private @Nullable String email;

  private @Nullable String phoneNumber;

  @NotBlank private String firstName;

  @NotBlank private String lastName;

  @NotNull private Instant createdDate;

  @NotNull private Instant updatedDate;

  @NotNull @Builder.Default private Boolean active = true;

  public int getAge() {
    return PersonalCode.getAge(personalCode);
  }

  public boolean hasReachedRetirementAge() {
    return PersonalCode.getAge(personalCode) >= PersonalCode.getRetirementAge(personalCode);
  }

  public Optional<Member> getMember() {
    return Optional.ofNullable(member);
  }

  public Member getMemberOrThrow() {
    return getMember().orElseThrow(NotAMemberException::new);
  }

  public boolean hasName() {
    return firstName != null || lastName != null;
  }

  @PrePersist
  protected void onCreate() {
    createdDate = clock().instant();
    updatedDate = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedDate = Instant.now();
  }

  public boolean isMember() {
    return getMember().isPresent();
  }

  public boolean hasContactDetails() {
    return email != null || phoneNumber != null;
  }

  public Long getMemberId() {
    return getMemberOrThrow().getId();
  }

  @Override
  public String code() {
    return getPersonalCode();
  }

  @Override
  public String name() {
    return getFullName();
  }

  @JsonIgnore
  public Long getIdOrThrow() {
    return requireNonNull(id, "User id missing: personalCode=" + personalCode);
  }
}
