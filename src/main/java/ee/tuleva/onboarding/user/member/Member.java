package ee.tuleva.onboarding.user.member;

import static ee.tuleva.onboarding.time.ClockHolder.clock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.Where;

@Data
@Builder
@Entity
@Table(name = "member")
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"user"})
@Where(clause = "active = true")
public class Member implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JsonIgnore
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @NotNull private Integer memberNumber;

  @NotNull private Instant createdDate;

  @NotNull @Builder.Default private Boolean active = true;

  @PrePersist
  protected void onCreate() {
    createdDate = clock().instant();
  }
}
