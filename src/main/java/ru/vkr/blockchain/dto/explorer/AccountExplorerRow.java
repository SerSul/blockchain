package ru.vkr.blockchain.dto.explorer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.vkr.blockchain.domain.model.enums.AccountRole;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountExplorerRow {

    private String address;
    private List<AccountRole> roles;
    private boolean active;
    private boolean inValidatorRoundRobin;
    private boolean bootstrapValidator;
}
