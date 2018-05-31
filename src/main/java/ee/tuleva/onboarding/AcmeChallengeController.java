package ee.tuleva.onboarding;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AcmeChallengeController {

	@RequestMapping(value = "/.well-known/acme-challenge/EW8WoDb74_B7ObIDV2OPS_JVwllolN9lLSxmGEHXkLE")
	@ResponseBody
	public String challenge() {
		return "EW8WoDb74_B7ObIDV2OPS_JVwllolN9lLSxmGEHXkLE.EMEBBxvSam3n_ien1J0z4dXeTuc2JuR3HqfAP6teLjE";
	}

}
