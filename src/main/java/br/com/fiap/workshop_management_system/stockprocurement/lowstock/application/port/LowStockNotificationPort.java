package br.com.fiap.workshop_management_system.stockprocurement.lowstock.application.port;

import br.com.fiap.workshop_management_system.stockprocurement.lowstock.application.event.LowStockDetectedEvent;

public interface LowStockNotificationPort {

    void notifyLowStockDetected(LowStockDetectedEvent event);
}
