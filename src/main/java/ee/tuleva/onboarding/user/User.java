package ee.tuleva.onboarding.user;

import lombok.Builder;
import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@Entity
@Table(name = "users")
public class User implements Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String personalCode;

	private String firstName;

	private String lastName;

	private Instant createdDate;

	private Integer memberNumber;

}
