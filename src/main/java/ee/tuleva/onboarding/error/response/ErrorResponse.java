package ee.tuleva.onboarding.error.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Getter
@Setter
@JsonInclude(NON_NULL)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ErrorResponse {
	private String code;
	private String message;
	private String path;
	@Builder.Default
	private List<String> arguments = new ArrayList<>();
}
