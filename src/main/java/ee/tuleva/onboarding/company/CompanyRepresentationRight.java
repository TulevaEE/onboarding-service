package ee.tuleva.onboarding.company;

import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static jakarta.persistence.GenerationType.UUID;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "company_representation_right")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CompanyRepresentationRight {

  @Id
  @GeneratedValue(strategy = UUID)
  private UUID id;

  @NotNull
  @Column(nullable = false)
  private UUID companyId;

  private @Nullable Long entryId;

  private @Nullable String representationType;

  private @Nullable String representationTypeText;

  private @Nullable String content;

  private @Nullable LocalDate startDate;

  private @Nullable LocalDate endDate;

  @Column(nullable = false, updatable = false)
  private Instant createdDate;

  @PrePersist
  void prePersist() {
    if (createdDate == null) {
      createdDate = clock().instant();
    }
  }
}
