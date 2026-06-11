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
@Table(name = "aml_third_pillar_alert")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmlThirdPillarAlert {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String transactionFingerprint;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AmlAlertType alertType;

  @Column(nullable = false)
  private Instant alertedAt;
}
