package ee.tuleva.onboarding.company;

import static jakarta.persistence.GenerationType.UUID;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

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

  private Long entryId;

  private String representationType;

  private String representationTypeText;

  private String content;

  private LocalDate startDate;

  private LocalDate endDate;

  @Column(nullable = false, updatable = false)
  private Instant createdDate;

  @PrePersist
  void prePersist() {
    if (createdDate == null) {
      createdDate = Instant.now();
    }
  }
}
