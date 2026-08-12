package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class QueueManagementService {

    private static final long DEFAULT_AVERAGE_SERVICE_MINUTES = 5;

    private final RegistrationRepository registrationRepository;
    private final QueueRepository queueRepository;
    private final QueueManagerRepository queueManagerRepository;
    private final PasswordEncoder passwordEncoder;
    private final QueueViewService queueViewService;

    public QueueManagementService(
            RegistrationRepository registrationRepository,
            QueueRepository queueRepository,
            QueueManagerRepository queueManagerRepository,
            PasswordEncoder passwordEncoder,
            QueueViewService queueViewService
    ) {
        this.registrationRepository = registrationRepository;
        this.queueRepository = queueRepository;
        this.queueManagerRepository = queueManagerRepository;
        this.passwordEncoder = passwordEncoder;
        this.queueViewService = queueViewService;
    }

    @Transactional
    public QueueResponse createQueue(QueueCreateRequest request) {
        RegistrationEntity registration = registrationRepository.findById(request.registrationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Qeydiyyat tapilmadi."));

        String address = normalizeRequired(request.address(), "Unvan mutleqdir.");
        String serviceName = normalizeRequired(request.serviceName(), "Isin adi mutleqdir.");
        List<String> categories = sanitizeCategories(request.categories());

        if (categories.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "En azi bir kateqoriya elave edin.");
        }
        if (registration.getRegistrationType() == RegistrationType.FERDI
                && queueRepository.countByRegistrationId(registration.getId()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ferdi qeydiyyat yalniz bir novbe yarada biler.");
        }

        QueueEntity queue = new QueueEntity();
        queue.setRegistration(registration);
        queue.setAddress(address);
        queue.setServiceName(serviceName);
        queue.setCategories(categories);
        queue.setQrToken(UUID.randomUUID().toString());
        queue.setCurrentServingNumber(0);
        queue.setLastIssuedNumber(0);
        queue.setAverageServiceMinutes(DEFAULT_AVERAGE_SERVICE_MINUTES);
        queue.setServedCustomersCount(0);
        queue.setTotalServiceMinutes(0);
        queue.setLastAdvancedAt(null);
        queue.setResetMode(resolveResetMode(request.resetMode()));
        queue.setResetAt(resolveResetAt(queue.getResetMode(), request.resetAt()));
        queue.setActive(true);

        QueueEntity savedQueue = queueRepository.save(queue);
        createManagerIfNeeded(savedQueue, registration, request);
        return queueViewService.toQueueResponse(savedQueue);
    }

    @Transactional
    public List<QueueResponse> getQueues(long registrationId) {
        if (!registrationRepository.existsById(registrationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Qeydiyyat tapilmadi.");
        }

        return queueRepository.findByRegistrationIdOrderByIdAsc(registrationId)
                .stream()
                .map(this::ensureQueueState)
                .map(queueViewService::toQueueResponse)
                .toList();
    }

    @Transactional
    public List<QueueResponse> getPublicQueues() {
        return queueRepository.findAll()
                .stream()
                .map(this::ensureQueueState)
                .filter(QueueEntity::isActive)
                .sorted(Comparator.comparing(QueueEntity::getId).reversed())
                .map(queueViewService::toQueueResponse)
                .toList();
    }

    @Transactional
    public QueueDetailResponse getQueueDetail(long queueId, Long registrationId, Long queueManagerId) {
        QueueEntity queue = ensureQueueState(findQueue(queueId));
        validateQueueAccess(queue, registrationId, queueManagerId);
        return queueViewService.toQueueDetailResponse(queue);
    }

    @Transactional
    public QueueDetailResponse advanceQueue(long queueId, QueueAdvanceRequest request) {
        QueueEntity queue = ensureQueueState(findQueue(queueId));
        validateQueueAccess(queue, request.registrationId(), request.queueManagerId());
        if (!queue.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deaktiv novbeni idare etmek olmur.");
        }
        if (queue.getCurrentServingNumber() >= queue.getLastIssuedNumber()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Novbede gozleyen yoxdur.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (queue.getCurrentServingNumber() > 0 && queue.getLastAdvancedAt() != null) {
            long elapsedMinutes = Math.max(1, Duration.between(queue.getLastAdvancedAt(), now).toMinutes());
            queue.setTotalServiceMinutes(queue.getTotalServiceMinutes() + elapsedMinutes);
            queue.setServedCustomersCount(queue.getServedCustomersCount() + 1);
            queue.setAverageServiceMinutes(Math.max(1, queue.getTotalServiceMinutes() / queue.getServedCustomersCount()));
        }

        queue.setCurrentServingNumber(queue.getCurrentServingNumber() + 1);
        queue.setLastAdvancedAt(now);
        return queueViewService.toQueueDetailResponse(queueRepository.save(queue));
    }

    @Transactional
    public QueueDetailResponse resetQueue(long queueId, QueueResetRequest request) {
        QueueEntity queue = ensureQueueState(findQueue(queueId));
        validateQueueAccess(queue, request.registrationId(), request.queueManagerId());
        queue.setCurrentServingNumber(0);
        queue.setLastIssuedNumber(0);
        queue.setServedCustomersCount(0);
        queue.setTotalServiceMinutes(0);
        queue.setAverageServiceMinutes(DEFAULT_AVERAGE_SERVICE_MINUTES);
        queue.setLastAdvancedAt(null);
        if (queue.getResetMode() == QueueResetMode.MANUAL) {
            queue.setActive(true);
        }
        return queueViewService.toQueueDetailResponse(queueRepository.save(queue));
    }

    QueueEntity findQueue(long queueId) {
        return queueRepository.findById(queueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Novbe tapilmadi."));
    }

    QueueEntity ensureQueueState(QueueEntity queue) {
        if (!queue.isActive() || queue.getRegistration().getStatus() == RegistrationStatus.EXPIRED) {
            if (queue.isActive() && queue.getRegistration().getStatus() == RegistrationStatus.EXPIRED) {
                queue.setActive(false);
                queueRepository.save(queue);
            }
            return queue;
        }
        if (queue.getResetMode() == QueueResetMode.MANUAL || queue.getResetAt() == null) {
            return queue;
        }
        if (!LocalDateTime.now().isBefore(queue.getResetAt())) {
            queue.setActive(false);
            queue.setCurrentServingNumber(0);
            queue.setLastIssuedNumber(0);
            queue.setLastAdvancedAt(null);
            return queueRepository.save(queue);
        }
        return queue;
    }

    void validateQueueAccess(QueueEntity queue, Long registrationId, Long queueManagerId) {
        boolean ownerAccess = registrationId != null && queue.getRegistration().getId().equals(registrationId);
        boolean managerAccess = false;

        if (queueManagerId != null) {
            QueueManagerEntity manager = queueManagerRepository.findById(queueManagerId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Queue user tapilmadi."));
            managerAccess = manager.getQueue().getId().equals(queue.getId());
        }

        if (!ownerAccess && !managerAccess) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu novbeni idare etmek icazeniz yoxdur.");
        }
    }

    private void createManagerIfNeeded(QueueEntity savedQueue, RegistrationEntity registration, QueueCreateRequest request) {
        if (registration.getRegistrationType() != RegistrationType.KORPORATIV) {
            return;
        }

        String managerUsername = normalizeRequired(request.managerUsername(), "Korporativ novbe ucun queue user username mutleqdir.").toLowerCase();
        String managerPassword = normalizeRequired(request.managerPassword(), "Korporativ novbe ucun queue user password mutleqdir.");

        if (!managerUsername.matches("[A-Za-z0-9._-]{3,100}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idareci adi 3-100 simvol olmalidir.");
        }
        if (managerPassword.length() < 8 || managerPassword.length() > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idareci sifresi 8-72 simvol olmalidir.");
        }
        if (queueManagerRepository.existsByUsername(managerUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu queue user username artiq movcuddur.");
        }

        QueueManagerEntity manager = new QueueManagerEntity();
        manager.setQueue(savedQueue);
        manager.setUsername(managerUsername);
        manager.setPasswordHash(passwordEncoder.encode(managerPassword));
        queueManagerRepository.save(manager);
    }

    private String normalizeRequired(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
        return value.trim();
    }

    private List<String> sanitizeCategories(List<String> categories) {
        if (categories == null) {
            return List.of();
        }
        return categories.stream()
                .filter(category -> category != null && !category.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private QueueResetMode resolveResetMode(String value) {
        if (value == null || value.isBlank()) {
            return QueueResetMode.DAILY;
        }
        try {
            return QueueResetMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset novu duzgun deyil.");
        }
    }

    private LocalDateTime resolveResetAt(QueueResetMode resetMode, String value) {
        if (resetMode == QueueResetMode.MANUAL) {
            return null;
        }
        if (resetMode == QueueResetMode.DAILY) {
            return LocalDate.now().plusDays(1).atStartOfDay();
        }
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Secilen tarix mutleqdir.");
        }
        try {
            return LocalDate.parse(value.trim()).plusDays(1).atStartOfDay();
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tarix YYYY-MM-DD formatinda olmalidir.");
        }
    }
}
