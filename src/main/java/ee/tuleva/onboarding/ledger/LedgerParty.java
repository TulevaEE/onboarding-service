package ee.tuleva.onboarding.ledger;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static org.hibernate.generator.EventType.INSERT;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.Type;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "party", schema = "ledger")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class LedgerParty {

  public enum PartyType {
    USER,
    LEGAL_ENTITY
  }

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private UUID id;

  @Enumerated(STRING)
  @Column(nullable = false, columnDefinition = "ledger.party_type")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private PartyType partyType;

  @NotNull
  // TODO currently personal ID, can add party representative logic later for children and companies
  private String ownerId;

  @Type(JsonType.class)
  @Column(columnDefinition = "JSONB", nullable = false)
  private Map<String, Object> details;

  @Column(nullable = false, updatable = false, insertable = false)
  @Generated(event = INSERT)
  private Instant createdAt;
}
