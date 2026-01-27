package ee.tuleva.onboarding.investment.report;

import static jakarta.persistence.EnumType.STRING;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

@Data
@Builder
@Entity
@Table(name = "investment_report")
@AllArgsConstructor
@NoArgsConstructor
public class InvestmentReport {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @Enumerated(STRING)
  private ReportProvider provider;

  @NotNull
  @Enumerated(STRING)
  private ReportType reportType;

  @NotNull private LocalDate reportDate;

  @NotNull
  @Builder.Default
  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  @Convert(disableConversion = true)
  private List<Map<String, Object>> rawData = List.of();

  @NotNull
  @Builder.Default
  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  @Convert(disableConversion = true)
  private Map<String, Object> metadata = Map.of();

  private Instant createdAt;
}
