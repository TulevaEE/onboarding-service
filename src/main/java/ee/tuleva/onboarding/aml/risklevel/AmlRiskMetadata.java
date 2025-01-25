package ee.tuleva.onboarding.aml.risklevel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "v_aml_risk_metadata", schema = "analytics")
public class AmlRiskMetadata {

  @Id
  @Column(name = "personal_id")
  private String personalId;

  @Column(name = "risk_level")
  private Integer riskLevel;

  @Column(name = "metadata")
  private String metadata;
}
