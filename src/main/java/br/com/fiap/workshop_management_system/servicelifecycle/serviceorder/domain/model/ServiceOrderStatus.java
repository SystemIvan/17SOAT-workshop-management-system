package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model;

public enum ServiceOrderStatus {
    RECEIVED,
    IN_DIAGNOSIS,
    AWAITING_APPROVAL,
    AWAITING_ITEMS,
    IN_PROGRESS,
    COMPLETED,
    DELIVERED;

    /**
     * RF38 - prioridade operacional para a listagem padrão de ordens de serviço (menor rank aparece
     * primeiro). Não usa {@link #ordinal()} para não acoplar esta regra de negócio à ordem de
     * declaração do enum.
     */
    public int listingRank() {
        return switch (this) {
            case IN_PROGRESS -> 1;
            case AWAITING_APPROVAL -> 2;
            case IN_DIAGNOSIS -> 3;
            case RECEIVED -> 4;
            case AWAITING_ITEMS -> 5;
            case COMPLETED -> 6;
            case DELIVERED -> 7;
        };
    }
}
