package br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model;

public class InvalidLowStockPolicyException extends IllegalArgumentException {

    public InvalidLowStockPolicyException(String message) {
        super(message);
    }
}
