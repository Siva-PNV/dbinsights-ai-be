package edu.dbinsight.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long> {
    void deleteByUserUserId(Long userId);

    Optional<PasswordResetTokenEntity> findFirstByUserUserIdOrderByCreatedAtDesc(Long userId);

    List<PasswordResetTokenEntity> findByUserUserIdAndConsumedAtIsNull(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordResetTokenEntity token where token.tokenHash = :tokenHash")
    Optional<PasswordResetTokenEntity> findForUpdateByTokenHash(@Param("tokenHash") String tokenHash);
}
