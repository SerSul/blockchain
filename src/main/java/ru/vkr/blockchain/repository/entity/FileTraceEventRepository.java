package ru.vkr.blockchain.repository.entity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.blockchain.domain.entity.FileTraceEvent;
import ru.vkr.blockchain.domain.model.enums.FileTraceEventType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FileTraceEventRepository extends JpaRepository<FileTraceEvent, String> {

    List<FileTraceEvent> findByFileHashAndEventTypeOrderByRecordedAtAsc(String fileHash, FileTraceEventType eventType);

    List<FileTraceEvent> findAllByOrderByRecordedAtDesc(Pageable pageable);

    List<FileTraceEvent> findByRecordedAtAfterOrderByRecordedAtAsc(LocalDateTime since, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT MAX(e.recordedAt) FROM FileTraceEvent e")
    Optional<LocalDateTime> findLatestRecordedAt();
}
