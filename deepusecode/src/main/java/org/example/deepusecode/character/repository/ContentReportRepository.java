package org.example.deepusecode.character.repository;

import org.example.deepusecode.character.entity.ContentReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContentReportRepository extends JpaRepository<ContentReport, Long> {

    /** 审核队列：待处理的按时间正序，先来先审 */
    Page<ContentReport> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    /** 已处理的按时间倒序，方便回看 */
    Page<ContentReport> findByStatusNotOrderByHandledAtDesc(String status, Pageable pageable);

    long countByStatus(String status);

    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, String targetType, Long targetId);

    /** 同一内容的全部举报：处理其中一条时，把其余的也一起结掉 */
    List<ContentReport> findByTargetTypeAndTargetId(String targetType, Long targetId);

    long countByTargetTypeAndTargetIdAndStatus(String targetType, Long targetId, String status);

    Optional<ContentReport> findById(Long id);
}
