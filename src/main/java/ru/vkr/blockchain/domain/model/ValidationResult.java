package ru.vkr.blockchain.domain.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    private Boolean isValid;
    private String errorMessage;
    private String validationType;
    private LocalDateTime timestamp;

    public ValidationResult(Boolean isValid, String errorMessage, String validationType) {
        this.isValid = isValid;
        this.errorMessage = errorMessage;
        this.validationType = validationType;
        this.timestamp = LocalDateTime.now();
    }

    public static ValidationResult success(String validationType) {
        return new ValidationResult(true, null, validationType);
    }

    public static ValidationResult failure(String validationType, String errorMessage) {
        return new ValidationResult(false, errorMessage, validationType);
    }

    public boolean isSuccess() {
        return isValid != null && isValid;
    }

    public String getDetails() {
        if (isSuccess()) {
            return validationType + " validation passed";
        }
        return validationType + " validation failed: " + errorMessage;
    }
}
