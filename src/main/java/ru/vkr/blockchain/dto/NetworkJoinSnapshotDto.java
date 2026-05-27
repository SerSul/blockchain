package ru.vkr.blockchain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Снимок состояния сети для присоединения новой ноды (LevelDB: аккаунты + списки валидаторов). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NetworkJoinSnapshotDto {

    @JsonProperty("validator_addresses")
    private List<String> validatorAddresses;

    @JsonProperty("bootstrap_validator_addresses")
    private List<String> bootstrapValidatorAddresses;

    private List<AccountSyncDto> accounts;
}
