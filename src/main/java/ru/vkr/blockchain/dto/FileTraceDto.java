package ru.vkr.blockchain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileTraceDto {

    @JsonProperty("file_hash")
    private String fileHash;

    @JsonProperty("version_chain")
    private List<TransactionDto> versionChain;

    @JsonProperty("downloads")
    private List<FileTraceEventDto> downloads;
}
