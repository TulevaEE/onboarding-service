package ee.tuleva.onboarding.capital;

import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.user.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

@Data
@Builder
@Entity
@Table(name = "initial_capital")
@AllArgsConstructor
@NoArgsConstructor
public class InitialCapital {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(InitialCapitalView.SkipUserField.class)
    private Long id;

    @NotNull
    @JsonView(InitialCapitalView.SkipUserField.class)
    BigDecimal amount;

    @NotBlank
    @Size(min = 3, max = 3)
    @JsonView(InitialCapitalView.SkipUserField.class)
    String currency;

    @OneToOne
    User user;

}
