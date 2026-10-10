package edu.dbinsight.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationTokenEntity, Long> {
    Optional<EmailVerificationTokenEntity> findByUserUserId(Long userId);

    void deleteByUserUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from EmailVerificationTokenEntity token where token.tokenHash = :tokenHash")
    Optional<EmailVerificationTokenEntity> findForUpdateByTokenHash(@Param("tokenHash") String tokenHash);
}
