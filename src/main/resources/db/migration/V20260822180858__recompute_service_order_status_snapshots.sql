UPDATE service_orders service_order
SET status_snapshot = CASE
    WHEN status_snapshot = 'DELIVERED' THEN 'DELIVERED'
    WHEN EXISTS (
        SELECT 1
        FROM service_executions execution
        WHERE execution.service_order_id = service_order.id
    ) AND NOT EXISTS (
        SELECT 1
        FROM service_executions execution
        WHERE execution.service_order_id = service_order.id
          AND execution.status NOT IN ('COMPLETED', 'REJECTED')
    ) THEN 'COMPLETED'
    WHEN EXISTS (
        SELECT 1
        FROM service_executions execution
        WHERE execution.service_order_id = service_order.id
          AND execution.status IN ('READY', 'IN_PROGRESS')
    ) THEN 'IN_PROGRESS'
    WHEN EXISTS (
        SELECT 1
        FROM service_executions execution
        WHERE execution.service_order_id = service_order.id
          AND execution.status = 'AWAITING_ITEMS'
    ) THEN 'AWAITING_ITEMS'
    WHEN has_sent_estimate_with_pending_lines THEN 'AWAITING_APPROVAL'
    WHEN open_diagnosis_id IS NOT NULL THEN 'IN_DIAGNOSIS'
    ELSE 'RECEIVED'
END;
