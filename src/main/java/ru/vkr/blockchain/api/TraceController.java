package ru.vkr.blockchain.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vkr.blockchain.dto.ApiResponse;
import ru.vkr.blockchain.dto.FileTraceDto;
import ru.vkr.blockchain.dto.FileTraceEventDto;
import ru.vkr.blockchain.dto.RecordDownloadRequest;
import ru.vkr.blockchain.service.FileTraceService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/trace")
@RequiredArgsConstructor
@Slf4j
public class TraceController {

    private final FileTraceService fileTraceService;

    @GetMapping("/files/{fileHash}")
    public ResponseEntity<ApiResponse<FileTraceDto>> getFileTrace(@PathVariable String fileHash) {
        return ResponseEntity.ok(ApiResponse.success(fileTraceService.getFileTrace(fileHash)));
    }

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<List<FileTraceEventDto>>> listEvents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok(ApiResponse.success(fileTraceService.listEventsSince(since, limit)));
    }

    @PostMapping("/downloads")
    public ResponseEntity<ApiResponse<FileTraceEventDto>> recordDownload(
            @RequestBody @Valid RecordDownloadRequest request) {
        log.info("Off-chain trace: download fileHash={}", request.getFileHash());
        return ResponseEntity.ok(ApiResponse.success(fileTraceService.recordDownload(request)));
    }
}
