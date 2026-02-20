package ee.tuleva.onboarding.administration;

import static org.hibernate.type.SqlTypes.JSON;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

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
  @JdbcTypeCode(JSON)
  private List<Map<String, Object>> content = List.of();
}
