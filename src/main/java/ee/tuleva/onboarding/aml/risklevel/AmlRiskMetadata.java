package ee.tuleva.onboarding.aml.risklevel;

import static org.hibernate.type.SqlTypes.JSON;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Immutable
@Table(name = "v_aml_risk_metadata", schema = "analytics")
public class AmlRiskMetadata {

  @Id
  @Column(name = "personal_id")
  private String personalId;

  @Column(name = "risk_level")
  private Integer riskLevel;

  @JdbcTypeCode(JSON)
  @Column(name = "metadata")
  private Map<String, Object> metadata;
}
