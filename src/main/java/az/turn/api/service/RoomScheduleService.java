package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RoomScheduleService {
    private final WeeklyAvailabilityRuleRepository ruleRepository;
    private final AvailabilityExceptionRepository exceptionRepository;
    private final ProviderAccessService accessService;
    private final ProviderInputService inputService;
    private final RoomConfigurationMapper mapper;

    public RoomScheduleService(
            WeeklyAvailabilityRuleRepository ruleRepository,
            AvailabilityExceptionRepository exceptionRepository,
            ProviderAccessService accessService,
            ProviderInputService inputService,
            RoomConfigurationMapper mapper
    ) {
        this.ruleRepository = ruleRepository;
        this.exceptionRepository = exceptionRepository;
        this.accessService = accessService;
        this.inputService = inputService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<WeeklyAvailabilityRuleDto> getWeeklyRules(long roomId, long userId) {
        accessService.requireRoomViewer(roomId, userId);
        return ruleRepository.findByRoomIdOrderByDayOfWeekAscStartTimeAsc(roomId)
                .stream().map(mapper::toDto).toList();
    }

    @Transactional
    public List<WeeklyAvailabilityRuleDto> replaceWeeklyRules(
            long roomId,
            long userId,
            WeeklyAvailabilityReplaceRequestDto request
    ) {
        RoomEntity room = accessService.requireEditableRoom(roomId, userId);
        validateWeeklyRules(request.rules());
        if (room.getStatus() == RoomStatus.PUBLISHED && request.rules().stream().noneMatch(rule -> rule.active())) {
            throw conflict("Yayımlanmış otağın ən azı bir aktiv iş intervalı olmalıdır.");
        }
        ruleRepository.deleteByRoomId(roomId);
        ruleRepository.flush();
        List<WeeklyAvailabilityRuleEntity> saved = ruleRepository.saveAllAndFlush(
                request.rules().stream().map(rule -> newRule(room, rule)).toList()
        );
        return saved.stream().sorted(ruleComparator()).map(mapper::toDto).toList();
    }

    @Transactional
    public List<WeeklyAvailabilityRuleDto> copyWeeklyRules(
            long roomId,
            long userId,
            WeeklyAvailabilityCopyRequestDto request
    ) {
        RoomEntity room = accessService.requireEditableRoom(roomId, userId);
        if (request.targetDays().contains(request.sourceDay())) {
            throw badRequest("Mənbə gün hədəf günlər arasında ola bilməz.");
        }
        List<WeeklyAvailabilityRuleEntity> source = ruleRepository
                .findByRoomIdAndDayOfWeekOrderByStartTimeAsc(roomId, request.sourceDay());
        for (DayOfWeek targetDay : request.targetDays()) {
            ruleRepository.deleteByRoomIdAndDayOfWeek(roomId, targetDay);
            ruleRepository.flush();
            List<WeeklyAvailabilityRuleEntity> copies = source.stream()
                    .map(rule -> copyRule(room, targetDay, rule))
                    .toList();
            ruleRepository.saveAll(copies);
        }
        ruleRepository.flush();
        if (room.getStatus() == RoomStatus.PUBLISHED && ruleRepository.countByRoomIdAndActiveTrue(roomId) < 1) {
            throw conflict("Yayımlanmış otağın ən azı bir aktiv iş intervalı olmalıdır.");
        }
        return ruleRepository.findByRoomIdOrderByDayOfWeekAscStartTimeAsc(roomId)
                .stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AvailabilityExceptionDto> getExceptions(long roomId, long userId) {
        accessService.requireRoomViewer(roomId, userId);
        return exceptionRepository.findByRoomIdOrderByDateAscStartTimeAsc(roomId)
                .stream().map(mapper::toDto).toList();
    }

    @Transactional
    public AvailabilityExceptionDto createException(
            long roomId,
            long userId,
            AvailabilityExceptionUpsertRequestDto request
    ) {
        RoomEntity room = accessService.requireEditableRoom(roomId, userId);
        validateException(request, null, roomId);
        AvailabilityExceptionEntity exception = new AvailabilityExceptionEntity();
        exception.setRoom(room);
        applyException(exception, request);
        return mapper.toDto(exceptionRepository.save(exception));
    }

    @Transactional
    public AvailabilityExceptionDto updateException(
            long roomId,
            long exceptionId,
            long userId,
            AvailabilityExceptionUpsertRequestDto request
    ) {
        accessService.requireEditableRoom(roomId, userId);
        AvailabilityExceptionEntity exception = exceptionRepository.findByIdAndRoomId(exceptionId, roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Xüsusi cədvəl qeydi tapılmadı."));
        validateException(request, exceptionId, roomId);
        applyException(exception, request);
        return mapper.toDto(exceptionRepository.save(exception));
    }

    @Transactional
    public void deleteException(long roomId, long exceptionId, long userId) {
        accessService.requireEditableRoom(roomId, userId);
        AvailabilityExceptionEntity exception = exceptionRepository.findByIdAndRoomId(exceptionId, roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Xüsusi cədvəl qeydi tapılmadı."));
        exceptionRepository.delete(exception);
    }

    private void validateWeeklyRules(List<WeeklyAvailabilityRuleRequestDto> rules) {
        Map<DayOfWeek, List<WeeklyAvailabilityRuleRequestDto>> activeByDay = new EnumMap<>(DayOfWeek.class);
        Set<String> uniqueIntervals = new HashSet<>();
        for (WeeklyAvailabilityRuleRequestDto rule : rules) {
            validateInterval(rule.startTime(), rule.endTime(), "İş intervalının başlanğıcı bitmədən əvvəl olmalıdır.");
            String key = rule.dayOfWeek() + ":" + rule.startTime() + ":" + rule.endTime();
            if (!uniqueIntervals.add(key)) throw conflict("Eyni iş intervalı təkrar göndərilib.");
            if (rule.active()) activeByDay.computeIfAbsent(rule.dayOfWeek(), day -> new ArrayList<>()).add(rule);
        }
        for (List<WeeklyAvailabilityRuleRequestDto> dayRules : activeByDay.values()) {
            dayRules.sort(Comparator.comparing(WeeklyAvailabilityRuleRequestDto::startTime));
            for (int index = 1; index < dayRules.size(); index++) {
                if (dayRules.get(index).startTime().isBefore(dayRules.get(index - 1).endTime())) {
                    throw conflict("Eyni gün üçün aktiv iş intervalları üst-üstə düşə bilməz.");
                }
            }
        }
    }

    private void validateException(
            AvailabilityExceptionUpsertRequestDto request,
            Long currentId,
            long roomId
    ) {
        if (request.type() == AvailabilityExceptionType.CLOSED) {
            if (request.startTime() != null || request.endTime() != null) {
                throw badRequest("Bağlı gün üçün başlanğıc və bitmə saatı verilməməlidir.");
            }
        } else {
            validateInterval(request.startTime(), request.endTime(), "Xüsusi intervalın başlanğıcı bitmədən əvvəl olmalıdır.");
        }
        List<AvailabilityExceptionEntity> existing = exceptionRepository
                .findByRoomIdAndDateOrderByStartTimeAsc(roomId, request.date())
                .stream().filter(item -> !item.getId().equals(currentId)).toList();
        if (request.type() == AvailabilityExceptionType.CLOSED && !existing.isEmpty()) {
            throw conflict("Bağlı gün başqa xüsusi intervallarla birlikdə istifadə edilə bilməz.");
        }
        if (request.type() != AvailabilityExceptionType.CLOSED
                && existing.stream().anyMatch(item -> item.getType() == AvailabilityExceptionType.CLOSED)) {
            throw conflict("Bağlı günə əlavə interval daxil edilə bilməz.");
        }
        if (request.type() != AvailabilityExceptionType.CLOSED && existing.stream()
                .filter(item -> item.getType() == request.type())
                .anyMatch(item -> overlaps(request.startTime(), request.endTime(), item.getStartTime(), item.getEndTime()))) {
            throw conflict("Eyni növlü xüsusi intervallar üst-üstə düşə bilməz.");
        }
    }

    private void validateInterval(LocalTime start, LocalTime end, String message) {
        if (start == null || end == null || !start.isBefore(end)) throw badRequest(message);
    }

    private boolean overlaps(LocalTime start, LocalTime end, LocalTime otherStart, LocalTime otherEnd) {
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }

    private WeeklyAvailabilityRuleEntity newRule(RoomEntity room, WeeklyAvailabilityRuleRequestDto request) {
        WeeklyAvailabilityRuleEntity rule = new WeeklyAvailabilityRuleEntity();
        rule.setRoom(room);
        rule.setDayOfWeek(request.dayOfWeek());
        rule.setStartTime(request.startTime());
        rule.setEndTime(request.endTime());
        rule.setActive(request.active());
        return rule;
    }

    private WeeklyAvailabilityRuleEntity copyRule(
            RoomEntity room,
            DayOfWeek targetDay,
            WeeklyAvailabilityRuleEntity source
    ) {
        WeeklyAvailabilityRuleEntity rule = new WeeklyAvailabilityRuleEntity();
        rule.setRoom(room);
        rule.setDayOfWeek(targetDay);
        rule.setStartTime(source.getStartTime());
        rule.setEndTime(source.getEndTime());
        rule.setActive(source.isActive());
        return rule;
    }

    private void applyException(
            AvailabilityExceptionEntity exception,
            AvailabilityExceptionUpsertRequestDto request
    ) {
        exception.setDate(request.date());
        exception.setType(request.type());
        exception.setStartTime(request.startTime());
        exception.setEndTime(request.endTime());
        exception.setReason(inputService.optional(request.reason()));
    }

    private Comparator<WeeklyAvailabilityRuleEntity> ruleComparator() {
        return Comparator.comparing(WeeklyAvailabilityRuleEntity::getDayOfWeek)
                .thenComparing(WeeklyAvailabilityRuleEntity::getStartTime);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
