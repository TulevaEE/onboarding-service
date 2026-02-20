package ee.tuleva.onboarding.user.member;

import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Data
@Builder
@Entity
@Table(name = "member")
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"user"})
@SQLRestriction("active = true")
public class Member implements Serializable {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  @JsonIgnore
  @OneToOne(fetch = LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @NotNull private Integer memberNumber;

  @NotNull private Instant createdDate;

  @NotNull @Builder.Default private Boolean active = true;

  @PrePersist
  protected void onCreate() {
    createdDate = clock().instant();
  }

  public String getFirstName() {
    return user.getFirstName();
  }

  public String getLastName() {
    return user.getLastName();
  }

  public String getFullName() {
    return user.getFullName();
  }
}
