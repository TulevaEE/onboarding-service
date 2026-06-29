package ee.tuleva.onboarding.aml.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "aml_tkf_volume_alert")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmlTkfVolumeAlert {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String personalId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AmlAlertType alertType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TkfFlowDirection direction;

  @Column(nullable = false)
  private String windowKey;

  @Column(nullable = false)
  private Instant alertedAt;
}
