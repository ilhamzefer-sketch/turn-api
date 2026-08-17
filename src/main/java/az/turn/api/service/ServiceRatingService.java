package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ServiceRatingService {
    private final ServiceRatingRepository ratingRepository;
    private final LiveQueueEntryRepository liveEntryRepository;
    private final PlannedBookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final ProviderAccessService accessService;
    private final Clock clock;

    public ServiceRatingService(
            ServiceRatingRepository ratingRepository,
            LiveQueueEntryRepository liveEntryRepository,
            PlannedBookingRepository bookingRepository,
            UserRepository userRepository,
            RoomRepository roomRepository,
            ProviderAccessService accessService,
            Clock clock
    ) {
        this.ratingRepository = ratingRepository;
        this.liveEntryRepository = liveEntryRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.accessService = accessService;
        this.clock = clock;
    }

    @Transactional
    public ServiceRatingDto upsertLive(long userId, long entryId, RatingUpsertRequestDto request) {
        LiveQueueEntryEntity entry = liveEntryRepository.findById(entryId)
                .orElseThrow(() -> notFound("Canlı növbə girişi tapılmadı."));
        if (entry.getStatus() != LiveQueueEntryStatus.COMPLETED) {
            throw conflict("Yalnız tamamlanmış xidmət qiymətləndirilə bilər.");
        }
        requireCustomer(entry.getUser(), entry.getGuestContact(), userId);
        ServiceRatingEntity rating = ratingRepository.findByLiveQueueEntryId(entryId)
                .orElseGet(() -> newLiveRating(entry, userId));
        return save(rating, request);
    }

    @Transactional
    public ServiceRatingDto upsertBooking(long userId, long bookingId, RatingUpsertRequestDto request) {
        PlannedBookingEntity booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> notFound("Rezervasiya tapılmadı."));
        if (booking.getStatus() != PlannedBookingStatus.COMPLETED) {
            throw conflict("Yalnız tamamlanmış xidmət qiymətləndirilə bilər.");
        }
        requireCustomer(booking.getUser(), booking.getGuestContact(), userId);
        ServiceRatingEntity rating = ratingRepository.findByPlannedBookingId(bookingId)
                .orElseGet(() -> newBookingRating(booking, userId));
        return save(rating, request);
    }

    @Transactional(readOnly = true)
    public RoomRatingSummaryDto summary(long roomId) {
        RoomEntity room = roomRepository.findById(roomId)
                .orElseThrow(() -> notFound("Otaq tapılmadı."));
        if (room.getStatus() != RoomStatus.PUBLISHED || room.getVisibility() == RoomVisibility.PRIVATE) {
            throw notFound("Otaq tapılmadı.");
        }
        return new RoomRatingSummaryDto(
                roomId,
                Optional.ofNullable(ratingRepository.averageScoreByRoomId(roomId)).orElse(0.0),
                ratingRepository.countByRoomId(roomId)
        );
    }

    @Transactional(readOnly = true)
    public List<ServiceRatingDto> roomRatings(long roomId, long userId) {
        accessService.requireRoomViewer(roomId, userId);
        return ratingRepository.findByRoomIdOrderByCreatedAtDesc(roomId).stream().map(this::toDto).toList();
    }

    private ServiceRatingDto save(ServiceRatingEntity rating, RatingUpsertRequestDto request) {
        if (rating.getId() != null && LocalDateTime.now(clock).isAfter(rating.getEditableUntil())) {
            throw conflict("Qiymətləndirmənin yeddi günlük redaktə müddəti bitib.");
        }
        rating.setScore(request.score());
        rating.setComment(normalizeComment(request.comment()));
        return toDto(ratingRepository.save(rating));
    }

    private ServiceRatingEntity newLiveRating(LiveQueueEntryEntity entry, long userId) {
        ServiceRatingEntity rating = baseRating(userId, entry.getRoom());
        rating.setLiveQueueEntry(entry);
        return rating;
    }

    private ServiceRatingEntity newBookingRating(PlannedBookingEntity booking, long userId) {
        ServiceRatingEntity rating = baseRating(userId, booking.getRoom());
        rating.setPlannedBooking(booking);
        return rating;
    }

    private ServiceRatingEntity baseRating(long userId, RoomEntity room) {
        ServiceRatingEntity rating = new ServiceRatingEntity();
        rating.setCustomerUser(userRepository.findById(userId).orElseThrow(() -> notFound("İstifadəçi tapılmadı.")));
        rating.setRoom(room);
        rating.setEditableUntil(LocalDateTime.now(clock).plusDays(7));
        return rating;
    }

    private void requireCustomer(UserEntity directUser, GuestContactEntity guest, long userId) {
        Long ownerId = directUser != null
                ? directUser.getId()
                : guest == null || guest.getLinkedUser() == null ? null : guest.getLinkedUser().getId();
        if (ownerId == null || ownerId != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu xidməti qiymətləndirə bilməzsiniz.");
        }
    }

    private ServiceRatingDto toDto(ServiceRatingEntity rating) {
        boolean live = rating.getLiveQueueEntry() != null;
        return new ServiceRatingDto(
                rating.getId(), rating.getRoom().getId(), live ? "LIVE_QUEUE" : "PLANNED_BOOKING",
                live ? rating.getLiveQueueEntry().getId() : rating.getPlannedBooking().getId(),
                rating.getScore(), rating.getComment(), rating.getCreatedAt(), rating.getUpdatedAt(),
                rating.getEditableUntil()
        );
    }

    private String normalizeComment(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
