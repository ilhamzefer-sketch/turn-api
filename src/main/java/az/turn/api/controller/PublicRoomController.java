package az.turn.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/public")
public class PublicRoomController {
    private final PublicRoomQueryService queryService;

    public PublicRoomController(PublicRoomQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/categories")
    public List<PublicCategoryDto> categories() {
        return queryService.categories();
    }

    @GetMapping("/rooms")
    public PublicRoomSearchPageDto rooms(
            @RequestParam(required = false) @Size(max = 120) String q,
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(required = false) @Size(max = 120) String city,
            @RequestParam(required = false) @Size(max = 120) String district,
            @RequestParam(required = false) ReservationMode mode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(24) int size
    ) {
        return queryService.search(q, categoryId, city, district, mode, page, size);
    }

    @GetMapping("/rooms/{roomId}")
    public PublicRoomProfileDto room(@PathVariable @Positive long roomId) {
        return queryService.profile(roomId);
    }

    @GetMapping("/qr/{token}")
    public PublicQrResolutionDto qr(
            @PathVariable
            @NotBlank
            @Size(max = 128)
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "QR kod düzgün formatda deyil.")
            String token
    ) {
        return queryService.resolveQr(token);
    }
}
