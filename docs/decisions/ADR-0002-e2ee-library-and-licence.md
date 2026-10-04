# ADR-0002: E2EE library and client licence

- Status: proposed (decision gate before Phase 2 completes)
- Date: 2026-10-04
- Deciders: product owner, counsel (pending)

## Context

The requirement is an audited Signal-protocol implementation: PQXDH session
setup (post-quantum where available) + Double Ratchet + Sender Keys for groups.
The reference implementation, **libsignal**, is published under **AGPL-3.0**
(verified against the upstream project). Shipping a proprietary client that links
AGPL code without a commercial exception would violate the licence.

## Options

1. **Open-source the client under AGPL-3.0** (Signal-style, with an app-store
   distribution exception drafted by counsel).
2. **Obtain a commercial licence** from Signal.
3. **Use an alternative audited stack** (Tink primitives + an RFC 9420 MLS
   implementation) — avoids copyleft but increases protocol-integration risk and
   loses PQXDH parity.

## Decision (interim)

Phase 0/1 architecture keeps the E2EE engine behind the Section 6.6 interface set
in `:core:crypto` so any of the three options is a contained swap. Integration
work begins only once counsel confirms the licence path.

## Consequences

- No libsignal dependency is declared anywhere in Phase 0.
- The crypto interface layer and attachment cipher (AES-256-GCM, JCA) are
  engine-agnostic and already test-covered.
- Risk: delay if the licence decision slips past the Phase 2 start.
