package ee.tuleva.onboarding.user;

import static ee.tuleva.onboarding.time.ClockHolder.clock;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.notification.email.Emailable;
import ee.tuleva.onboarding.user.exception.NotAMemberException;
import ee.tuleva.onboarding.user.member.Member;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;
import lombok.*;

@Data
@Builder
@Entity
@Table(name = "users")
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"member"})
@ToString(exclude = {"member"})
public class User implements Person, Emailable, Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(cascade = CascadeType.ALL, mappedBy = "user", fetch = FetchType.LAZY)
  Member member;

  @ValidPersonalCode private String personalCode;

  @Email private String email;

  private String phoneNumber;

  @NotBlank private String firstName;

  @NotBlank private String lastName;

  @NotNull private Instant createdDate;

  @NotNull private Instant updatedDate;

  @NotNull @Builder.Default private Boolean active = true;

  @Min(18)
  public int getAge() {
    return PersonalCode.getAge(personalCode);
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
}
