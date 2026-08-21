package az.turn.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PublicRoomQueryService {
    private final RoomRepository roomRepository;
    private final RoomAssignmentRepository assignmentRepository;
    private final ServiceRatingRepository ratingRepository;
    private final BusinessCategoryRepository categoryRepository;
    private final QrCredentialService qrCredentialService;

    public PublicRoomQueryService(
            RoomRepository roomRepository,
            RoomAssignmentRepository assignmentRepository,
            ServiceRatingRepository ratingRepository,
            BusinessCategoryRepository categoryRepository,
            QrCredentialService qrCredentialService
    ) {
        this.roomRepository = roomRepository;
        this.assignmentRepository = assignmentRepository;
        this.ratingRepository = ratingRepository;
        this.categoryRepository = categoryRepository;
        this.qrCredentialService = qrCredentialService;
    }

    @Transactional(readOnly = true)
    public List<PublicCategoryDto> categories() {
        return categoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAzAsc().stream()
                .map(this::toCategory)
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicRoomSearchPageDto search(
            String query,
            Long categoryId,
            String city,
            String district,
            ReservationMode mode,
            int page,
            int size
    ) {
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))
        );
        Page<RoomEntity> rooms = roomRepository.searchPublic(
                contains(query),
                categoryId,
                exact(city),
                exact(district),
                mode,
                RoomStatus.PUBLISHED,
                RoomVisibility.PUBLIC,
                pageRequest
        );
        List<Long> roomIds = rooms.getContent().stream().map(RoomEntity::getId).toList();
        Map<Long, RoomRatingAggregate> ratings = ratings(roomIds);
        List<PublicRoomSummaryDto> items = rooms.getContent().stream()
                .map(room -> toSummary(room, ratings.get(room.getId())))
                .toList();
        return new PublicRoomSearchPageDto(
                items,
                rooms.getNumber(),
                rooms.getSize(),
                rooms.getTotalElements(),
                rooms.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public PublicRoomProfileDto profile(long roomId) {
        RoomEntity room = requirePubliclyAccessible(roomId);
        List<RoomAssignmentEntity> assignments = assignmentRepository
                .findByRoomIdInAndStatusOrderByCreatedAtAsc(List.of(roomId), RoomAssignmentStatus.ACTIVE);
        RoomRatingAggregate rating = ratings(List.of(roomId)).get(roomId);
        List<PublicRoomOwnerDto> owners = assignments.stream()
                .filter(value -> value.getUser().getStatus() == UserStatus.ACTIVE)
                .map(this::toOwner)
                .toList();
        return toProfile(room, owners, rating);
    }

    @Transactional(readOnly = true)
    public PublicQrResolutionDto resolveQr(String token) {
        RoomEntity resolved = qrCredentialService.resolveActiveRoom(token);
        RoomEntity room = requirePubliclyAccessible(resolved.getId());
        return new PublicQrResolutionDto(
                room.getId(),
                room.getReservationMode(),
                "/rooms/" + room.getId()
        );
    }

    private RoomEntity requirePubliclyAccessible(long roomId) {
        return roomRepository.findPubliclyAccessibleById(
                        roomId,
                        RoomStatus.PUBLISHED,
                        RoomVisibility.PRIVATE
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Otaq tapılmadı."));
    }

    private PublicRoomSummaryDto toSummary(
            RoomEntity room,
            RoomRatingAggregate rating
    ) {
        return new PublicRoomSummaryDto(
                room.getId(),
                room.getName(),
                room.getDescription(),
                room.getReservationMode(),
                providerName(room),
                room.getBranch() == null ? null : room.getBranch().getName(),
                category(room),
                customSubcategory(room),
                location(room),
                average(rating),
                count(rating)
        );
    }

    private PublicRoomProfileDto toProfile(
            RoomEntity room,
            List<PublicRoomOwnerDto> owners,
            RoomRatingAggregate rating
    ) {
        BusinessEntity business = room.getBranch() == null ? null : room.getBranch().getBusiness();
        return new PublicRoomProfileDto(
                room.getId(),
                room.getName(),
                room.getRoomNumberOrCode(),
                room.getDescription(),
                room.getTimezone(),
                room.getReservationMode(),
                room.getDefaultSlotDurationMinutes(),
                room.getAppointmentBufferMinutes(),
                room.isLiveQueueAcceptingNewEntries(),
                providerName(room),
                business == null ? null : business.getDescription(),
                business == null ? null : business.getLogoUrl(),
                room.getBranch() == null ? null : room.getBranch().getName(),
                category(room),
                customSubcategory(room),
                location(room),
                contactPhone(room, owners),
                owners,
                average(rating),
                count(rating)
        );
    }

    private PublicRoomOwnerDto toOwner(RoomAssignmentEntity assignment) {
        UserEntity user = assignment.getUser();
        String displayName = (user.getFirstName() + " " + user.getLastName()).trim();
        String phone = assignment.isShowPhonePublicly() ? user.getNormalizedPhone() : null;
        return new PublicRoomOwnerDto(displayName, phone);
    }

    private PublicRoomLocationDto location(RoomEntity room) {
        if (room.getBranch() != null) {
            BranchEntity branch = room.getBranch();
            return new PublicRoomLocationDto(
                    branch.getAddress(),
                    branch.getCity(),
                    branch.getDistrict(),
                    branch.getLatitude(),
                    branch.getLongitude()
            );
        }
        if (room.getPersonalPublicAddress() == null) return null;
        return new PublicRoomLocationDto(
                room.getPersonalPublicAddress(),
                null,
                null,
                room.getPersonalLatitude(),
                room.getPersonalLongitude()
        );
    }

    private String contactPhone(RoomEntity room, List<PublicRoomOwnerDto> owners) {
        String ownerPhone = owners.stream()
                .map(PublicRoomOwnerDto::phone)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
        if (ownerPhone != null || room.getBranch() == null) return ownerPhone;
        BranchEntity branch = room.getBranch();
        return branch.getNormalizedPhone() == null
                ? branch.getBusiness().getNormalizedPhone()
                : branch.getNormalizedPhone();
    }

    private String providerName(RoomEntity room) {
        return room.getBranch() == null
                ? room.getIndividualWorkspace().getName()
                : room.getBranch().getBusiness().getName();
    }

    private PublicCategoryDto category(RoomEntity room) {
        if (room.getBranch() == null) return null;
        return toCategory(room.getBranch().getBusiness().getCategory());
    }

    private String customSubcategory(RoomEntity room) {
        return room.getBranch() == null ? null : room.getBranch().getBusiness().getCustomSubcategory();
    }

    private PublicCategoryDto toCategory(BusinessCategoryEntity category) {
        if (category == null) return null;
        return new PublicCategoryDto(category.getId(), category.getCode(), category.getNameAz());
    }

    private Map<Long, RoomRatingAggregate> ratings(List<Long> roomIds) {
        if (roomIds.isEmpty()) return Map.of();
        return ratingRepository.summarizeByRoomIds(roomIds).stream()
                .collect(Collectors.toMap(RoomRatingAggregate::getRoomId, Function.identity()));
    }

    private double average(RoomRatingAggregate rating) {
        return rating == null || rating.getAverageScore() == null ? 0.0 : rating.getAverageScore();
    }

    private long count(RoomRatingAggregate rating) {
        return rating == null || rating.getRatingCount() == null ? 0 : rating.getRatingCount();
    }

    private String contains(String value) {
        String normalized = exact(value);
        return normalized == null ? null : "%" + normalized + "%";
    }

    private String exact(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
