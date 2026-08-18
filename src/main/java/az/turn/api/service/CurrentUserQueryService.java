package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurrentUserQueryService {
    private final UserRepository userRepository;

    public CurrentUserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public CurrentUserDto get(long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "İstifadəçi tapılmadı."));
        return new CurrentUserDto(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getNormalizedPhone(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
