package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception;

public class SupplierOrderRejectedException extends RuntimeException {

    private final String rejectionCode;

    public SupplierOrderRejectedException(String rejectionCode) {
        super("External supplier rejected the purchase order");
        this.rejectionCode = rejectionCode;
    }

    public String rejectionCode() {
        return rejectionCode;
    }
}
