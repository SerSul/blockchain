package ru.vkr.blockchain.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.vkr.blockchain.dto.BlockDto;
import ru.vkr.blockchain.dto.FileTraceDto;
import ru.vkr.blockchain.dto.FileTraceEventDto;
import ru.vkr.blockchain.dto.PageResponse;
import ru.vkr.blockchain.dto.TransactionDto;
import ru.vkr.blockchain.domain.entity.BlockMetadata;
import ru.vkr.blockchain.service.BlockQueryService;
import ru.vkr.blockchain.service.FileTraceService;
import ru.vkr.blockchain.service.NetworkOverviewService;
import ru.vkr.blockchain.service.TransactionQueryService;

import ru.vkr.blockchain.domain.model.enums.TransactionType;

import java.util.Optional;

@Controller
@RequestMapping("/explorer")
@RequiredArgsConstructor
public class BlockExplorerController {

    private final BlockQueryService blockQueryService;
    private final TransactionQueryService transactionQueryService;
    private final NetworkOverviewService networkOverviewService;
    private final FileTraceService fileTraceService;

    @GetMapping({"", "/"})
    public String root() {
        return "redirect:/explorer/blocks";
    }

    @GetMapping("/blocks")
    public String listBlocks(
            Model model,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) Integer heightFrom,
            @RequestParam(required = false) Integer heightTo,
            @RequestParam(required = false) String validatorAddress) {
        PageResponse<BlockMetadata> result = blockQueryService.getBlocks(
                heightFrom, heightTo, validatorAddress, page, size, "height", "desc");
        model.addAttribute("page", result);
        model.addAttribute("heightFrom", heightFrom);
        model.addAttribute("heightTo", heightTo);
        model.addAttribute("validatorAddress", validatorAddress);
        model.addAttribute("size", size);
        model.addAttribute("networkStatus", networkOverviewService.getStatus());
        model.addAttribute("validators", networkOverviewService.getValidators());
        return "explorer/blocks";
    }

    @GetMapping("/validators")
    public String listValidators(Model model) {
        model.addAttribute("validators", networkOverviewService.getValidators());
        model.addAttribute("networkStatus", networkOverviewService.getStatus());
        return "explorer/validators";
    }

    @GetMapping("/accounts")
    public String listAccounts(Model model) {
        model.addAttribute("accounts", networkOverviewService.getAccountsForExplorer());
        model.addAttribute("networkStatus", networkOverviewService.getStatus());
        return "explorer/accounts";
    }

    @GetMapping("/pending")
    public String listPendingTransactions(
            Model model,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<TransactionDto> result = transactionQueryService.getPendingTransactionsPage(page, size);
        model.addAttribute("page", result);
        model.addAttribute("size", size);
        return "explorer/pending-transactions";
    }

    @GetMapping("/fork-candidates")
    public String listForkCandidates(
            Model model,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<BlockDto> result = blockQueryService.getForkCandidatesPage(page, size);
        model.addAttribute("page", result);
        model.addAttribute("size", size);
        return "explorer/fork-candidates";
    }

    @GetMapping("/blocks/{hash}")
    public String blockDetail(@PathVariable String hash, Model model) {
        Optional<BlockDto> block = blockQueryService.getBlockByHash(hash);
        if (block.isPresent()) {
            model.addAttribute("block", block.get());
            model.addAttribute("metadataOnly", false);
        } else {
            blockQueryService.getBlockMetadataByHash(hash).ifPresentOrElse(
                    meta -> {
                        model.addAttribute("metadata", meta);
                        model.addAttribute("metadataOnly", true);
                    },
                    () -> model.addAttribute("notFound", true));
        }
        model.addAttribute("hash", hash);
        return "explorer/block-detail";
    }

    @GetMapping("/transactions/{id}")
    public String transactionDetail(@PathVariable String id, Model model) {
        Optional<TransactionDto> tx = transactionQueryService.getTransactionById(id);
        if (tx.isEmpty()) {
            model.addAttribute("notFound", true);
            model.addAttribute("id", id);
            return "explorer/transaction-detail";
        }
        model.addAttribute("tx", tx.get());
        if (tx.get().getBlockHash() != null) {
            model.addAttribute("blockLink", tx.get().getBlockHash());
        }
        if (tx.get().getTransactionType() == TransactionType.STORE_FILE && tx.get().getPayload() != null) {
            fileTraceService.extractFileHashFromStorePayload(tx.get().getPayload())
                    .ifPresent(hash -> model.addAttribute("fileHash", hash));
            if (tx.get().getPreviousTransactionId() == null) {
                fileTraceService.extractPreviousTransactionId(tx.get().getPayload())
                        .ifPresent(prevTransactionId -> tx.get().setPreviousTransactionId(prevTransactionId));
            }
        }
        return "explorer/transaction-detail";
    }

    @GetMapping("/trace")
    public String traceIndex(
            @RequestParam(required = false) String fileHash,
            Model model) {
        if (fileHash != null && fileHash.length() == 64) {
            return "redirect:/explorer/files/" + fileHash + "/trace";
        }
        model.addAttribute("recentEvents", fileTraceService.listRecent(50));
        return "explorer/file-trace-index";
    }

    @GetMapping("/files/{fileHash}/trace")
    public String fileTrace(@PathVariable String fileHash, Model model) {
        try {
            FileTraceDto trace = fileTraceService.getFileTrace(fileHash);
            model.addAttribute("trace", trace);
            model.addAttribute("fileHash", fileHash);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("fileHash", fileHash);
        }
        return "explorer/file-trace-detail";
    }
}
