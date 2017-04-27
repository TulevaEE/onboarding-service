package ee.tuleva.onboarding.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;

  User updateUser(String personalCode, String email, String phoneNumber) {
    User user = userRepository.findByPersonalCode(personalCode);
    user.setEmail(email);
    user.setPhoneNumber(phoneNumber);
    return userRepository.save(user);
  }

}
