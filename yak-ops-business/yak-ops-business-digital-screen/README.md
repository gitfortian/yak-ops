# Yak Ops Digital Screen

Digital Screen owns the persisted screen definition and its lightweight lifecycle. It does not execute Dataset queries; runtime data continues to flow through the Dataset module.

PR 1 intentionally persists one mutable screen definition with `DRAFT` / `PUBLISHED` status. Immutable published snapshots, version history and rollback belong to PR 2.

## API

- `GET /api/v1/digital-screens`
- `GET /api/v1/digital-screens/{id}`
- `POST /api/v1/digital-screens`
- `PUT /api/v1/digital-screens/{id}`
- `POST /api/v1/digital-screens/{id}/publish`
- `POST /api/v1/digital-screens/{id}/offline`
- `POST /api/v1/digital-screens/{id}/duplicate`
- `DELETE /api/v1/digital-screens/{id}`
