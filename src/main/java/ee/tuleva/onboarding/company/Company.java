package ee.tuleva.onboarding.company;

import static jakarta.persistence.GenerationType.UUID;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "company")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Company {

  @Id
  @GeneratedValue(strategy = UUID)
  private UUID id;

  @NotBlank
  @Column(unique = true, nullable = false)
  private String registryCode;

  @NotBlank
  @Column(nullable = false)
  private String name;

  @Column(nullable = false, updatable = false)
  private Instant createdDate;

  @PrePersist
  void prePersist() {
    if (createdDate == null) {
      createdDate = Instant.now();
    }
  }
}
