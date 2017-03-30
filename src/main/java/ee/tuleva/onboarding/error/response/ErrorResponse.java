package ee.tuleva.onboarding.error.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Getter
@JsonInclude(NON_NULL)
@Builder
public class ErrorResponse {
	private String code;
	private String message;
	private String path;
	private List<String> arguments = new ArrayList<>();
}
