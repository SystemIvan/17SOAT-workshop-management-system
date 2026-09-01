package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api;

import java.util.List;

public interface StockReservationApi {

    List<ReserveStockItemsResult> reserveAll(List<ReserveStockItemsCommand> commands);
}
