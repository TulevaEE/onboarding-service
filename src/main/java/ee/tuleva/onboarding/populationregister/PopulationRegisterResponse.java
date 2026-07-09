package ee.tuleva.onboarding.populationregister;

import static jakarta.persistence.EnumType.STRING;
import static org.hibernate.type.SqlTypes.JSON;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.jspecify.annotations.Nullable;

@Data
@Builder
@Entity
@Table(name = "population_register_response")
@AllArgsConstructor
@NoArgsConstructor
class PopulationRegisterResponse {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private String personalCode;

  @NotNull
  @Enumerated(STRING)
  private PopulationRegisterQueryType queryType;

  @NotNull private UUID messageId;

  @JdbcTypeCode(JSON)
  private @Nullable List<Map<String, Object>> response;

  @NotNull private Instant createdAt;
}
