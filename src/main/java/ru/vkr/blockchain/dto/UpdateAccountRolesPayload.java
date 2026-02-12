package ru.vkr.blockchain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.vkr.blockchain.domain.model.enums.AccountRole;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAccountRolesPayload {

    @NotNull(message = "Target address is required")
    @JsonProperty("target_address")
    private String targetAddress;

    @NotEmpty(message = "At least one role is required")
    private List<AccountRole> roles;
}
