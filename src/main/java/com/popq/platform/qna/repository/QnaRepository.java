package com.popq.platform.qna.repository;

import com.popq.platform.qna.entity.Qna;
import com.popq.platform.qna.entity.QnaCategory;
import com.popq.platform.qna.entity.QnaStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QnaRepository extends JpaRepository<Qna, Long> {

    // 사용자/관리자 카테고리, 답변상태별로 필터 조회 가능하게
    @Query(value = "SELECT q FROM Qna q " +
            "JOIN FETCH q.writer " +
            "LEFT JOIN FETCH q.answeredBy " +
            "WHERE (:category IS NULL OR q.category = :category) " +
            "AND (:status IS NULL OR q.status = :status) " +
            "ORDER BY q.createdAt DESC",
            countQuery = "SELECT COUNT(q) FROM Qna q " +
                    "WHERE (:category IS NULL OR q.category = :category) " +
                    "AND (:status IS NULL OR q.status = :status)")
    Page<Qna> findQnasWithFilters(
            @Param("category") QnaCategory category,
            @Param("status") QnaStatus status,
            Pageable pageable
    );
}