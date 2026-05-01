package ru.vkr.blockchain.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.vkr.blockchain.domain.entity.TransactionMetadata;
import ru.vkr.blockchain.domain.model.Transaction;
import ru.vkr.blockchain.dto.TransactionDto;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(target = "blockHash", ignore = true)
    @Mapping(target = "contentSize", source = "payload", qualifiedByName = "payloadSize")
    TransactionDto toDto(Transaction tx);

    @Mapping(target = "blockHash", source = "blockHash")
    @Mapping(target = "contentSize", source = "tx.payload", qualifiedByName = "payloadSize")
    TransactionDto toDto(Transaction tx, String blockHash);

    @Mapping(target = "payload", ignore = true)
    @Mapping(target = "signature", ignore = true)
    TransactionDto toDto(TransactionMetadata metadata);

    Transaction toDomain(TransactionDto dto);

    List<TransactionDto> toDtoList(List<Transaction> transactions);

    @Named("payloadSize")
    default Long payloadSize(String payload) {
        return payload == null ? null : (long) payload.getBytes(StandardCharsets.UTF_8).length;
    }
}
