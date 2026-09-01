package az.turn.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/users/me/wallet")
public class WalletController {
    private final RequestAuthenticationService authenticationService;
    private final WalletQueryService walletQueryService;
    private final WalletTopUpOptionsService topUpOptionsService;
    private final WalletTopUpRequestService topUpRequestService;

    public WalletController(
            RequestAuthenticationService authenticationService,
            WalletQueryService walletQueryService,
            WalletTopUpOptionsService topUpOptionsService,
            WalletTopUpRequestService topUpRequestService
    ) {
        this.authenticationService = authenticationService;
        this.walletQueryService = walletQueryService;
        this.topUpOptionsService = topUpOptionsService;
        this.topUpRequestService = topUpRequestService;
    }

    @GetMapping
    public WalletBalanceDto balance(Authentication authentication) {
        return walletQueryService.balance(userId(authentication));
    }

    @GetMapping("/top-up-options")
    public WalletTopUpOptionsDto topUpOptions(Authentication authentication) {
        userId(authentication);
        return topUpOptionsService.options();
    }

    @PostMapping("/top-up-requests")
    public WalletTopUpRequestDto createTopUpRequest(
            @Valid @RequestBody WalletTopUpCreateRequestDto request,
            Authentication authentication
    ) {
        return topUpRequestService.create(userId(authentication), request.packageCode());
    }

    @GetMapping("/top-up-requests/active")
    public WalletTopUpRequestDto activeTopUpRequest(Authentication authentication) {
        return topUpRequestService.active(userId(authentication));
    }

    @PostMapping(value = "/top-up-requests/{requestId}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WalletTopUpRequestDto uploadReceipt(
            @PathVariable long requestId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        return topUpRequestService.uploadReceipt(userId(authentication), requestId, file);
    }

    @GetMapping("/transactions")
    public WalletTransactionPageDto transactions(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Səhifə sıfırdan kiçik ola bilməz.") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Səhifə ölçüsü ən azı 1 olmalıdır.")
            @Max(value = 100, message = "Səhifə ölçüsü 100-dən böyük ola bilməz.") int size,
            Authentication authentication
    ) {
        return walletQueryService.transactions(userId(authentication), page, size);
    }

    private long userId(Authentication authentication) {
        return authenticationService.requireUser(authentication, AuthUserType.USER).userId();
    }
}
