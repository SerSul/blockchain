package ru.vkr.blockchain.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import ru.vkr.blockchain.domain.entity.BlockMetadata;
import ru.vkr.blockchain.domain.model.Block;
import ru.vkr.blockchain.domain.model.Transaction;
import ru.vkr.blockchain.dto.BlockDto;
import ru.vkr.blockchain.dto.PageResponse;
import ru.vkr.blockchain.dto.TransactionDto;
import ru.vkr.blockchain.repository.entity.BlockMetadataRepository;
import ru.vkr.blockchain.service.domain.BlockService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BlockQueryService {

    private final BlockMetadataRepository blockMetadataRepository;
    private final BlockService blockService;

    /**
     * Список блоков с фильтрами и пагинацией.
     *
     * @param heightFrom   минимальная высота (включительно), null — без ограничения
     * @param heightTo     максимальная высота (включительно), null — без ограничения
     * @param validatorAddress фильтр по валидатору, null — любой
     */
    public PageResponse<BlockMetadata> getBlocks(Integer heightFrom, Integer heightTo, String validatorAddress,
                                                  int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir != null && sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy != null ? sortBy : "height").ascending()
                : Sort.by(sortBy != null ? sortBy : "height").descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        boolean hasFilter = heightFrom != null || heightTo != null || (validatorAddress != null && !validatorAddress.isBlank());
        Page<BlockMetadata> result;
        if (!hasFilter) {
            result = blockMetadataRepository.findAllByOrderByHeightDesc(pageable);
        } else {
            Specification<BlockMetadata> spec = (root, query, cb) -> {
                var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                if (heightFrom != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("height"), heightFrom));
                }
                if (heightTo != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("height"), heightTo));
                }
                if (validatorAddress != null && !validatorAddress.isBlank()) {
                    predicates.add(cb.equal(root.get("validatorAddress"), validatorAddress));
                }
                return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            };
            result = blockMetadataRepository.findAll(spec, pageable);
        }
        return new PageResponse<>(
                result.getContent(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );
    }

    /**
     * Блок по хешу с полным списком транзакций (из LevelDB).
     */
    public Optional<BlockDto> getBlockByHash(String hash) {
        return blockService.findByHash(hash)
                .map(this::toBlockDto);
    }

    /**
     * Только метаданные блока по хешу (из JPA).
     */
    public Optional<BlockMetadata> getBlockMetadataByHash(String hash) {
        return blockMetadataRepository.findById(hash);
    }

    /**
     * Транзакции блока по его хешу (полные данные из LevelDB).
     */
    public List<TransactionDto> getBlockTransactions(String blockHash) {
        return blockService.findByHash(blockHash)
                .map(Block::getTransactions)
                .orElse(List.of())
                .stream()
                .map(tx -> toTransactionDto(tx, blockHash))
                .toList();
    }

    private BlockDto toBlockDto(Block block) {
        String blockHash = block.getCurrentHash();
        List<TransactionDto> txDtos = block.getTransactions() != null
                ? block.getTransactions().stream()
                    .map(tx -> toTransactionDto(tx, blockHash))
                    .toList()
                : List.of();
        return BlockDto.builder()
                .hash(block.getCurrentHash())
                .previousHash(block.getPreviousHash())
                .height(block.getHeight())
                .merkleRoot(block.getMerkleRoot())
                .validatorAddress(block.getValidatorAddress())
                .validatorSignature(block.getValidatorSignature())
                .transactionCount(block.getTransactions() != null ? block.getTransactions().size() : 0)
                .status(block.getStatus())
                .timestamp(block.getTimestamp())
                .transactions(txDtos)
                .build();
    }

    private TransactionDto toTransactionDto(Transaction tx, String blockHash) {
        return TransactionDto.builder()
                .id(tx.getId())
                .senderId(tx.getSenderId())
                .transactionType(tx.getTransactionType())
                .status(tx.getStatus())
                .blockHash(blockHash)
                .payloadHash(tx.getPayloadHash())
                .contentType(tx.getContentType())
                .contentSize(tx.getPayload() != null ? (long) tx.getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8).length : null)
                .timestamp(tx.getTimestamp())
                .payload(tx.getPayload())
                .build();
    }
}
