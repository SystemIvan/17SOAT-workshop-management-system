package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RF38 - prioridade operacional de listingRank(): IN_PROGRESS > AWAITING_APPROVAL > IN_DIAGNOSIS >
 * RECEIVED > AWAITING_ITEMS > COMPLETED > DELIVERED (menor rank aparece primeiro).
 */
class ServiceOrderStatusTest {

    @Test
    void listingRankFollowsTheOperationalPriorityOrder() {
        assertTrue(ServiceOrderStatus.IN_PROGRESS.listingRank() < ServiceOrderStatus.AWAITING_APPROVAL.listingRank());
        assertTrue(ServiceOrderStatus.AWAITING_APPROVAL.listingRank() < ServiceOrderStatus.IN_DIAGNOSIS.listingRank());
        assertTrue(ServiceOrderStatus.IN_DIAGNOSIS.listingRank() < ServiceOrderStatus.RECEIVED.listingRank());
        assertTrue(ServiceOrderStatus.RECEIVED.listingRank() < ServiceOrderStatus.AWAITING_ITEMS.listingRank());
        assertTrue(ServiceOrderStatus.AWAITING_ITEMS.listingRank() < ServiceOrderStatus.COMPLETED.listingRank());
        assertTrue(ServiceOrderStatus.COMPLETED.listingRank() < ServiceOrderStatus.DELIVERED.listingRank());
    }
}
