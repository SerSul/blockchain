package ru.vkr.blockchain.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;
import ru.vkr.blockchain.domain.model.Block;
import ru.vkr.blockchain.dto.BlockDto;
import ru.vkr.blockchain.dto.TransactionDto;

import java.util.List;

@Mapper(componentModel = "spring", uses = TransactionMapper.class)
public abstract class BlockMapper {

    @Autowired
    protected TransactionMapper transactionMapper;

    @Mapping(target = "hash", source = "currentHash")
    @Mapping(target = "transactionCount", expression = "java(block.getTransactions() != null ? block.getTransactions().size() : 0)")
    @Mapping(target = "transactions", ignore = true)
    public abstract BlockDto toDto(Block block);

    @Mapping(target = "currentHash", source = "hash")
    public abstract Block toDomain(BlockDto dto);

    public abstract List<BlockDto> toDtoList(List<Block> blocks);

    @AfterMapping
    protected void fillTransactions(Block block, @MappingTarget BlockDto dto) {
        if (block.getTransactions() == null) {
            dto.setTransactions(List.of());
            return;
        }
        List<TransactionDto> tx = block.getTransactions().stream()
                .map(item -> transactionMapper.toDto(item, block.getCurrentHash()))
                .toList();
        dto.setTransactions(tx);
    }
}
