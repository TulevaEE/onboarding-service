package ee.tuleva.onboarding.fund.manager;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotBlank;
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
  private Long id;

  public boolean isTuleva() {
    return TULEVA_FUND_MANAGER_NAME.equalsIgnoreCase(name);
  }
}
