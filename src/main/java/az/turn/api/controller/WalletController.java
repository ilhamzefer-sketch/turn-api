package az.turn.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/users/me/wallet")
public class WalletController {
    private final RequestAuthenticationService authenticationService;
    private final WalletQueryService walletQueryService;
    private final WalletTopUpOptionsService topUpOptionsService;

    public WalletController(
            RequestAuthenticationService authenticationService,
            WalletQueryService walletQueryService,
            WalletTopUpOptionsService topUpOptionsService
    ) {
        this.authenticationService = authenticationService;
        this.walletQueryService = walletQueryService;
        this.topUpOptionsService = topUpOptionsService;
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
