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
import ru.vkr.blockchain.dto.BlockDto;
import ru.vkr.blockchain.dto.PageResponse;
import ru.vkr.blockchain.dto.TransactionDto;
import ru.vkr.blockchain.mapper.BlockMapper;
import ru.vkr.blockchain.mapper.TransactionMapper;
import ru.vkr.blockchain.repository.entity.BlockMetadataRepository;
import ru.vkr.blockchain.service.LevelDBService;
import ru.vkr.blockchain.service.domain.BlockService;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BlockQueryService {

    private final BlockMetadataRepository blockMetadataRepository;
    private final BlockService blockService;
    private final LevelDBService levelDBService;
    private final BlockMapper blockMapper;
    private final TransactionMapper transactionMapper;

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
                .map(blockMapper::toDto);
    }

    /**
     * Только метаданные блока по хешу (из JPA).
     */
    public Optional<BlockMetadata> getBlockMetadataByHash(String hash) {
        return blockMetadataRepository.findById(hash);
    }

    public Optional<BlockMetadata> getLatestMetadata() {
        return blockMetadataRepository.findAllByOrderByHeightDesc(PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    public List<BlockDto> getBlocksRange(int fromHeightInclusive, int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 200));
        int toHeight = fromHeightInclusive + cappedLimit - 1;
        return blockMetadataRepository.findAllByHeightBetween(fromHeightInclusive, toHeight).stream()
                .map(BlockMetadata::getHash)
                .map(blockService::findByHash)
                .flatMap(Optional::stream)
                .map(blockMapper::toDto)
                .toList();
    }

    public PageResponse<BlockDto> getForkCandidatesPage(int page, int size) {
        List<BlockDto> all = levelDBService.scanPrefix("fork_candidate:").values().stream()
                .map(bytes -> {
                    try {
                        return Block.fromBytes(bytes);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator
                        .comparing(Block::getHeight, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed()
                        .thenComparing(Block::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(blockMapper::toDto)
                .toList();

        int safeSize = size > 0 ? size : 20;
        int total = all.size();
        int totalPages = safeSize > 0 ? (int) Math.ceil((double) total / safeSize) : 1;
        int from = Math.min(page * safeSize, total);
        int to = Math.min(from + safeSize, total);
        List<BlockDto> content = all.subList(from, to);
        return new PageResponse<>(content, total, totalPages, page, safeSize);
    }

    /**
     * Транзакции блока по его хешу (полные данные из LevelDB).
     */
    public List<TransactionDto> getBlockTransactions(String blockHash) {
        return blockService.findByHash(blockHash)
                .map(Block::getTransactions)
                .orElse(List.of())
                .stream()
                .map(tx -> transactionMapper.toDto(tx, blockHash))
                .toList();
    }
}
