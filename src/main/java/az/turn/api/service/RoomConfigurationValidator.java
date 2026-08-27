package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RoomConfigurationValidator {
    private final RoomAssignmentRepository assignmentRepository;
    private final WeeklyAvailabilityRuleRepository availabilityRepository;

    public RoomConfigurationValidator(
            RoomAssignmentRepository assignmentRepository,
            WeeklyAvailabilityRuleRepository availabilityRepository
    ) {
        this.assignmentRepository = assignmentRepository;
        this.availabilityRepository = availabilityRepository;
    }

    public void validateResetConfiguration(RoomEntity room) {
        LiveQueueResetPolicy policy = room.getLiveQueueResetPolicy();
        if (policy == null) {
            if (room.getLiveQueueResetLocalTime() != null || room.getLiveQueueResetIntervalMinutes() != null) {
                throw badRequest("Reset qaydası olmadan reset saatı və ya intervalı verilə bilməz.");
            }
            return;
        }
        if (policy == LiveQueueResetPolicy.DAILY_AT_TIME) {
            if (room.getLiveQueueResetLocalTime() == null || room.getLiveQueueResetIntervalMinutes() != null) {
                throw badRequest("Gündəlik reset üçün yalnız yerli reset saatı verilməlidir.");
            }
            return;
        }
        if (room.getLiveQueueResetLocalTime() != null || room.getLiveQueueResetIntervalMinutes() == null
                || room.getLiveQueueResetIntervalMinutes() <= 0) {
            throw badRequest("Interval reseti üçün yalnız müsbət interval verilməlidir.");
        }
    }

    public void validatePublishable(RoomEntity room) {
        if (room.getStatus() == RoomStatus.ARCHIVED) {
            throw conflict("Arxivdəki otaq yayımlana bilməz.");
        }
        if (room.getName() == null || room.getName().isBlank() || room.getReservationMode() == null
                || room.getVisibility() == null || room.getDefaultSlotDurationMinutes() < 1) {
            throw conflict("Otağın əsas məlumatları tamamlanmayıb.");
        }
        validateOwnerScope(room);
        if (assignmentRepository.countByRoomIdAndStatus(room.getId(), RoomAssignmentStatus.ACTIVE) < 1) {
            throw conflict("Otağı yayımlamaq üçün ən azı bir aktiv otaq sahibi olmalıdır.");
        }
        if (availabilityRepository.countByRoomIdAndActiveTrue(room.getId()) < 1) {
            throw conflict("Otağı yayımlamaq üçün həftəlik iş cədvəli yaradılmalıdır.");
        }
        if (room.getReservationMode() == ReservationMode.LIVE_QUEUE) {
            validateResetConfiguration(room);
            if (room.getLiveQueueResetPolicy() == null) {
                throw conflict("Canlı növbə otağı üçün reset qaydası seçilməlidir.");
            }
        } else if (room.getBookingWindowDays() < 1 || room.getBookingWindowDays() > 90
                || room.getCancellationCutoffMinutes() < 0) {
            throw conflict("Planlı otağın rezervasiya və ləğv parametrləri tamamlanmayıb.");
        }
    }

    public void validateOperationalReadiness(RoomEntity room) {
        if (room.getStatus() != RoomStatus.PUBLISHED) {
            throw conflict("Otağı istifadə etmək üçün məcburi mərhələləri tamamlayın və otağı yayımlayın.");
        }
        validatePublishable(room);
    }

    private void validateOwnerScope(RoomEntity room) {
        if (room.getBranch() != null) {
            if (room.getBranch().getStatus() != ProviderStatus.ACTIVE
                    || room.getBranch().getBusiness().getStatus() != ProviderStatus.ACTIVE) {
                throw conflict("Otağın biznesi və filialı aktiv olmalıdır.");
            }
            return;
        }
        if (room.getIndividualWorkspace() == null
                || room.getIndividualWorkspace().getStatus() != ProviderStatus.ACTIVE) {
            throw conflict("Otağın şəxsi workspace-i aktiv olmalıdır.");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
