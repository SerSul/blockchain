package ru.vkr.blockchain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoreDataPayload {

    @JsonProperty("fileName")
    private String fileName;

    @JsonProperty("fileHash")
    private String fileHash;

    @JsonProperty("size")
    private Long size;

    @JsonProperty("objectKey")
    private String objectKey;

    public boolean isFileReference() {
        return fileHash != null && !fileHash.isBlank();
    }

    public String resolveObjectKey() {
        if (objectKey != null && !objectKey.isBlank()) {
            return objectKey;
        }
        return fileHash;
    }
}
