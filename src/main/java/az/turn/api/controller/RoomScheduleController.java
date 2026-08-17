package az.turn.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/rooms/{roomId}")
public class RoomScheduleController {
    private final RoomScheduleService scheduleService;
    private final RequestAuthenticationService authenticationService;

    public RoomScheduleController(
            RoomScheduleService scheduleService,
            RequestAuthenticationService authenticationService
    ) {
        this.scheduleService = scheduleService;
        this.authenticationService = authenticationService;
    }

    @GetMapping("/availability-rules")
    public List<WeeklyAvailabilityRuleDto> weekly(
            @PathVariable @Positive long roomId,
            Authentication authentication
    ) {
        return scheduleService.getWeeklyRules(roomId, userId(authentication));
    }

    @PutMapping("/availability-rules")
    public List<WeeklyAvailabilityRuleDto> replaceWeekly(
            @PathVariable @Positive long roomId,
            @Valid @RequestBody WeeklyAvailabilityReplaceRequestDto request,
            Authentication authentication
    ) {
        return scheduleService.replaceWeeklyRules(roomId, userId(authentication), request);
    }

    @PostMapping("/availability-rules/copy")
    public List<WeeklyAvailabilityRuleDto> copyWeekly(
            @PathVariable @Positive long roomId,
            @Valid @RequestBody WeeklyAvailabilityCopyRequestDto request,
            Authentication authentication
    ) {
        return scheduleService.copyWeeklyRules(roomId, userId(authentication), request);
    }

    @GetMapping("/availability-exceptions")
    public List<AvailabilityExceptionDto> exceptions(
            @PathVariable @Positive long roomId,
            Authentication authentication
    ) {
        return scheduleService.getExceptions(roomId, userId(authentication));
    }

    @PostMapping("/availability-exceptions")
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilityExceptionDto createException(
            @PathVariable @Positive long roomId,
            @Valid @RequestBody AvailabilityExceptionUpsertRequestDto request,
            Authentication authentication
    ) {
        return scheduleService.createException(roomId, userId(authentication), request);
    }

    @PutMapping("/availability-exceptions/{exceptionId}")
    public AvailabilityExceptionDto updateException(
            @PathVariable @Positive long roomId,
            @PathVariable @Positive long exceptionId,
            @Valid @RequestBody AvailabilityExceptionUpsertRequestDto request,
            Authentication authentication
    ) {
        return scheduleService.updateException(roomId, exceptionId, userId(authentication), request);
    }

    @DeleteMapping("/availability-exceptions/{exceptionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteException(
            @PathVariable @Positive long roomId,
            @PathVariable @Positive long exceptionId,
            Authentication authentication
    ) {
        scheduleService.deleteException(roomId, exceptionId, userId(authentication));
    }

    private long userId(Authentication authentication) {
        return authenticationService.requireUser(authentication, AuthUserType.USER).userId();
    }
}
