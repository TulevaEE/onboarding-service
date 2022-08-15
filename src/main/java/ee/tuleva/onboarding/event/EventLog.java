package ee.tuleva.onboarding.event;

import java.time.Instant;
import java.util.Map;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

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

  @Type(type = "jsonb")
  @Column(columnDefinition = "jsonb")
  @Convert(disableConversion = true)
  private Map<String, Object> data;
}
