package ee.tuleva.onboarding.aml.risklevel;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;

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

  @Type(JsonType.class)
  @Convert(disableConversion = true)
  @Column(name = "metadata", columnDefinition = "jsonb")
  private Map<String, Object> metadata;
}
