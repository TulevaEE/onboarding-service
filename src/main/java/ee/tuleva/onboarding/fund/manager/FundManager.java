package ee.tuleva.onboarding.fund.manager;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Entity
@Table(name = "fund_manager")
@AllArgsConstructor
@NoArgsConstructor
public class FundManager {
  private static final String TULEVA_FUND_MANAGER_NAME = "Tuleva";

  @NotBlank String name;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @JsonIgnore
  private Long id;

  @JsonIgnore
  public boolean isTuleva() {
    return TULEVA_FUND_MANAGER_NAME.equalsIgnoreCase(name);
  }
}
