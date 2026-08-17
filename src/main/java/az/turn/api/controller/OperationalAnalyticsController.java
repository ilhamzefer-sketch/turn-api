package az.turn.api;

import jakarta.validation.constraints.Positive;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@Validated
@RequestMapping("/api")
public class OperationalAnalyticsController {
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final OperationalAnalyticsService analyticsService;
    private final OperationalReportExcelService excelService;
    private final RequestAuthenticationService authenticationService;

    public OperationalAnalyticsController(
            OperationalAnalyticsService analyticsService,
            OperationalReportExcelService excelService,
            RequestAuthenticationService authenticationService
    ) {
        this.analyticsService = analyticsService;
        this.excelService = excelService;
        this.authenticationService = authenticationService;
    }

    @GetMapping("/businesses/{businessId}/analytics")
    public OperationalAnalyticsDto business(
            @PathVariable @Positive long businessId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            Authentication authentication
    ) {
        return analyticsService.business(businessId, userId(authentication), from, to);
    }

    @GetMapping("/businesses/{businessId}/analytics.xlsx")
    public ResponseEntity<byte[]> businessExcel(
            @PathVariable @Positive long businessId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            Authentication authentication
    ) {
        OperationalAnalyticsDto report = analyticsService.business(businessId, userId(authentication), from, to);
        return excel("business-" + businessId + "-operations.xlsx", report);
    }

    @GetMapping("/rooms/{roomId}/analytics")
    public OperationalAnalyticsDto room(
            @PathVariable @Positive long roomId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            Authentication authentication
    ) {
        return analyticsService.room(roomId, userId(authentication), from, to);
    }

    @GetMapping("/rooms/{roomId}/analytics.xlsx")
    public ResponseEntity<byte[]> roomExcel(
            @PathVariable @Positive long roomId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            Authentication authentication
    ) {
        OperationalAnalyticsDto report = analyticsService.room(roomId, userId(authentication), from, to);
        return excel("room-" + roomId + "-operations.xlsx", report);
    }

    private ResponseEntity<byte[]> excel(String filename, OperationalAnalyticsDto report) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString()
                )
                .body(excelService.create(report));
    }

    private long userId(Authentication authentication) {
        return authenticationService.requireUser(authentication, AuthUserType.USER).userId();
    }
}
