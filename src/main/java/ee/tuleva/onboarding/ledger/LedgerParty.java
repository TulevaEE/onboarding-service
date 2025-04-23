package ee.tuleva.onboarding.ledger;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Getter
@Table(name = "party", schema = "ledger")
public class LedgerParty {

  public enum PartyType {
    USER, LEGAL_ENTITY
  }

  @Id
  @Column(nullable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "ledger.party_type")
  private PartyType type;

  @Column(nullable = false)
  private String name;

  @Type(JsonType.class)
  @Column(columnDefinition = "JSONB", nullable = false)
  private Map<String, Object> details;

  @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ")
  private Instant createdAt;
}
