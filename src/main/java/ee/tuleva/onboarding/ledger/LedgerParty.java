package ee.tuleva.onboarding.ledger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.Type;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "party", schema = "ledger")
@Getter
@Builder
@AllArgsConstructor
public class LedgerParty {

  public LedgerParty() {}

  public enum PartyType {
    USER,
    LEGAL_ENTITY
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(nullable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "ledger.party_type")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private PartyType type;

  @Column(nullable = false)
  private String name;

  @Type(JsonType.class)
  @Column(columnDefinition = "JSONB", nullable = false)
  private Map<String, Object> details;

  @OneToMany(mappedBy = "ledgerParty")
  @JsonIgnore
  private List<LedgerAccount> accounts;

  @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ")
  private Instant createdAt;
}
