package com.popq.platform.repository;

import com.popq.platform.entity.Role;
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

    boolean existsByUsername(String username);

    // 역할·상태로 회원 목록 조회 (매니저 배정 드롭다운 : MANAGER + ACTIVE), 이름순 [추가]
    @Query("SELECT u FROM User u WHERE u.role = :role AND u.status = :status ORDER BY u.name")
    List<User> findByRoleAndStatus(@Param("role") Role role, @Param("status") User.UserStatus status);

    @Query("SELECT u FROM User u WHERE u.status = :status AND u.withdrawnAt < :targetDate")
    List<User> findExpiredUsers(
            @Param("status") User.UserStatus status,
            @Param("targetDate") LocalDateTime targetDate
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT u
        FROM User u
        WHERE u.username = :username
    """)
    Optional<User> findByUsernameForUpdate(
            @Param("username") String username
    );
}