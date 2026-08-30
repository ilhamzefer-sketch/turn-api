package az.turn.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class WalletQueryService {
    private final WalletAccountRepository walletAccountRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletMapper walletMapper;

    public WalletQueryService(
            WalletAccountRepository walletAccountRepository,
            WalletTransactionRepository walletTransactionRepository,
            WalletMapper walletMapper
    ) {
        this.walletAccountRepository = walletAccountRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.walletMapper = walletMapper;
    }

    @Transactional(readOnly = true)
    public WalletBalanceDto balance(long userId) {
        return walletMapper.toBalanceDto(requireWallet(userId));
    }

    @Transactional(readOnly = true)
    public WalletTransactionPageDto transactions(long userId, int page, int size) {
        requireWallet(userId);
        Slice<WalletTransactionEntity> transactions = walletTransactionRepository
                .findByWalletAccountUserIdOrderByCreatedAtDescIdDesc(
                        userId,
                        PageRequest.of(page, size)
                );
        List<WalletTransactionDto> items = transactions.getContent().stream()
                .map(walletMapper::toTransactionDto)
                .toList();
        return new WalletTransactionPageDto(items, page, size, transactions.hasNext());
    }

    private WalletAccountEntity requireWallet(long userId) {
        return walletAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Balans hesabı tapılmadı."));
    }
}
