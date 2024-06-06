package ee.tuleva.onboarding.administration;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class PortfolioAnalytics {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private LocalDate date;

  @NotNull
  @Builder.Default
  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private List<Map<String, Object>> content = List.of();
}
