package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTopUpPackageRepository extends JpaRepository<WalletTopUpPackageEntity, String> {
    List<WalletTopUpPackageEntity> findByActiveTrueOrderByDisplayOrderAsc();
}
