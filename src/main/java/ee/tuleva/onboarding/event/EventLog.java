package ee.tuleva.onboarding.event;

import static org.hibernate.type.SqlTypes.JSON;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

@Data
@Builder
@Entity
@Table(name = "event_log")
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class EventLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Instant timestamp;

  private String principal;

  private String type;

  @JdbcTypeCode(JSON)
  private Map<String, Object> data;
}
