package ee.tuleva.onboarding.investment.report.publishing.data;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

@Getter
@Entity
@Table(name = "instrument_reference")
class InstrumentReference {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  private String isin;
  private String displayName;
  private String fundManager;
  private String country;
  private Instant createdAt;
  private Instant updatedAt;

  protected InstrumentReference() {}
}
