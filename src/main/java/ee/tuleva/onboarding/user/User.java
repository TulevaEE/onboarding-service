package ee.tuleva.onboarding.user;

import com.fasterxml.jackson.annotation.JsonView;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotBlank;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Past;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@Entity
@Table(name = "users")
@AllArgsConstructor
@NoArgsConstructor
public class User implements Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Size(min = 11, max = 11)
	private String personalCode;

	@NotNull
	@Email
	private String email;

	private String phoneNumber;

	@JsonView(UserView.Public.class)
	@NotBlank
	private String firstName;

	@JsonView(UserView.Public.class)
	@NotBlank
	private String lastName;

	@NotNull
	private Integer memberNumber;

	@NotNull
	@Past
	private Instant createdDate;

	@NotNull
	private Instant updatedDate;

	@NotNull
	private Boolean active;

	@JsonView(UserView.Public.class)
	public int getAge() {
		return PersonalCode.getAge(personalCode);
	}

}
