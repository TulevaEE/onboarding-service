package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.SIGNED;
import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static jakarta.persistence.EnumType.STRING;

import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateView;
import ee.tuleva.onboarding.mandate.content.CompositeMandateFileCreator;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Data
@Entity
@Table(name = "mandate_batch")
@NoArgsConstructor
public class MandateBatch {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @JsonView(MandateView.Default.class)
  @Nullable
  private Long id;

  @NotNull
  @Enumerated(STRING)
  private MandateBatchStatus status;

  @OneToMany(mappedBy = "mandateBatch", fetch = FetchType.EAGER)
  private List<Mandate> mandates;

  @NotNull
  @JsonView(MandateView.Default.class)
  private Instant createdDate;

  private byte @Nullable [] file;

  @Builder
  MandateBatch(MandateBatchStatus status, List<Mandate> mandates) {
    this.status = status;
    this.mandates = mandates;
    this.createdDate = clock().instant();
  }

  @PrePersist
  protected void onCreate() {
    createdDate = clock().instant();
  }

  private void addFiles(CompositeMandateFileCreator fileCreator) {

    //    mandates.stream()..
    //    this.file = fileCreator.getContentFiles();

  }

  public boolean isSigned() {
    return status == SIGNED;
  }
}
