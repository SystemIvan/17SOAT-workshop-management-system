# ADR 002: Notifications Are an Outbound Capability

**Status:** Accepted
**Date:** 2026-08-11

## Context

The domain flow contains policies that notify customers, technicians and stock managers. The current model does not give
notifications an independent lifecycle, vocabulary or persisted state.

## Decision

Notifications are not a bounded context in the current MVP. A module that needs delivery defines an outbound port in its
application layer and an infrastructure adapter implements the chosen channel. No unused shared notification abstraction
will be created in advance.

Notifications should be reconsidered as a bounded context when it owns business rules or state such as templates, user
preferences, channel selection, delivery attempts, retries or delivery history.

## Consequences

- Domain/application code does not depend directly on email, SMS or messaging providers.
- Delivery failures are handled according to the consuming use case until a dedicated notification model exists.
- A future notification context requires a new ADR and an explicit context-map update.
