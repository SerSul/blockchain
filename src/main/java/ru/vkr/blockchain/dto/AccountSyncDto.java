package ru.vkr.blockchain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.vkr.blockchain.domain.model.enums.AccountRole;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountSyncDto {

    private String address;

    @JsonProperty("public_key")
    private String publicKey;

    private List<AccountRole> roles;
    private boolean active;
}
