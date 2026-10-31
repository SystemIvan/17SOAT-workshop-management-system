package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;

/**
 * RF39 - representação dos 6 estados nominais do enunciado da Fase 2 para consumidores externos da API.
 * {@code AWAITING_ITEMS} e {@code IN_PROGRESS} mapeiam ambos para {@code EXECUCAO} (decisão registrada em
 * {@code docs/features/servicelifecycle/status-nominal-mapping/functional-spec.md}).
 */
public enum ServiceOrderStatusLabel {
    RECEBIDA,
    DIAGNOSTICO,
    AGUARDANDO_APROVACAO,
    EXECUCAO,
    FINALIZADA,
    ENTREGUE;

    public static ServiceOrderStatusLabel from(ServiceOrderStatus status) {
        return switch (status) {
            case RECEIVED -> RECEBIDA;
            case IN_DIAGNOSIS -> DIAGNOSTICO;
            case AWAITING_APPROVAL -> AGUARDANDO_APROVACAO;
            case IN_PROGRESS, AWAITING_ITEMS -> EXECUCAO;
            case COMPLETED -> FINALIZADA;
            case DELIVERED -> ENTREGUE;
        };
    }
}
