package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.command.CreatePurchaseOrderCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.command.PurchaseOrderLineCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.InvalidPurchaseOrderException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Component
public class PurchaseOrderCommandNormalizer {

    NormalizedPurchaseOrderCommand normalize(CreatePurchaseOrderCommand command) {
        if (command == null) {
            throw new InvalidPurchaseOrderException("Purchase order command must not be null");
        }
        if (command.lines().isEmpty() || command.lines().size() > 100 || command.demandIds().size() > 100) {
            throw new InvalidPurchaseOrderException(
                    "Purchase order must contain 1 to 100 lines and at most 100 demands");
        }

        List<UUID> demandIds = normalizeDemandIds(command.demandIds());
        List<PurchaseOrderLineCommand> lines = normalizeLines(command.lines());
        return new NormalizedPurchaseOrderCommand(demandIds, lines, hash(demandIds, lines));
    }

    private List<UUID> normalizeDemandIds(List<UUID> demandIds) {
        if (demandIds.stream().anyMatch(id -> id == null)) {
            throw new InvalidPurchaseOrderException("Purchase order demand ids must not contain null");
        }
        return demandIds.stream().distinct().sorted().toList();
    }

    private List<PurchaseOrderLineCommand> normalizeLines(List<PurchaseOrderLineCommand> lines) {
        Map<UUID, Integer> quantitiesByItem = new TreeMap<>();
        for (PurchaseOrderLineCommand line : lines) {
            if (line == null) {
                throw new InvalidPurchaseOrderException("Purchase order lines must not contain null");
            }
            try {
                quantitiesByItem.merge(line.stockItemId(), line.quantity(), Math::addExact);
            } catch (ArithmeticException exception) {
                throw new InvalidPurchaseOrderException(
                        "Purchase order quantity exceeds the supported range", exception);
            }
        }
        List<PurchaseOrderLineCommand> normalized = new ArrayList<>();
        quantitiesByItem.forEach((stockItemId, quantity) ->
                normalized.add(new PurchaseOrderLineCommand(stockItemId, quantity)));
        return List.copyOf(normalized);
    }

    private String hash(List<UUID> demandIds, List<PurchaseOrderLineCommand> lines) {
        StringBuilder canonical = new StringBuilder();
        demandIds.forEach(id -> canonical.append("demand:").append(id).append('\n'));
        lines.forEach(line -> canonical.append("line:")
                .append(line.stockItemId())
                .append(':')
                .append(line.quantity())
                .append('\n'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
