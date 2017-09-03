package ee.tuleva.onboarding;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AcmeChallengeController {

	@RequestMapping(value = "/.well-known/acme-challenge/K8qxt75RAplalO7SYsRwpeS6I_qmDbvCj5uGYFLueIA")
	@ResponseBody
	public String challenge() {
		return "K8qxt75RAplalO7SYsRwpeS6I_qmDbvCj5uGYFLueIA.EMEBBxvSam3n_ien1J0z4dXeTuc2JuR3HqfAP6teLjE";
	}

}
