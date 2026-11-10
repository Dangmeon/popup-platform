package com.popq.platform.repository;

import com.popq.platform.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    @Query("SELECT u FROM User u WHERE u.status = :status AND u.withdrawnAt < :targetDate")
    List<User> findExpiredUsers(
            @Param("status") User.UserStatus status,
            @Param("targetDate") LocalDateTime targetDate
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT u
        FROM User u
        WHERE u.id = :memberId
    """)
    Optional<User> findByIdForUpdate(
            @Param("memberId") Long memberId
    );
}