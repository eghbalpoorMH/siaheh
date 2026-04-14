# Siaheh API Contract V2 (Current Implementation)

## Identity / Profile
- `POST /api/v1/auth/otp/`
- `POST /api/v1/auth/tokens/`
- `PUT /api/v1/auth/tokens/`
- `DELETE /api/v1/auth/tokens/`
- `GET /api/v1/users/me/`
- `PATCH /api/v1/users/profile/`
- `GET /api/v1/users/search/?q=<query>`
- `POST /api/v1/users/discover-contacts/`

## Spaces / Members
- `GET /api/v1/spaces/`
- `POST /api/v1/spaces/`
- `GET /api/v1/spaces/{space_id}/`
- `POST /api/v1/spaces/{space_id}/convert/` with `{"confirm": true}`
- `GET /api/v1/spaces/{space_id}/members/`
- `POST /api/v1/spaces/{space_id}/members/add/`
`user_id` or `username` is accepted.
- `PATCH /api/v1/spaces/{space_id}/members/{user_id}/`
- `DELETE /api/v1/spaces/{space_id}/members/{user_id}/remove/`
- `PATCH /api/v1/spaces/{space_id}/preferences/`

## Entries / Timeline
- `GET /api/v1/spaces/{space_id}/messages/`
- `GET /api/v1/spaces/{space_id}/entries/` (compat alias)
- `POST /api/v1/spaces/{space_id}/messages/` multipart

## Entry List Response
- Paginated response shape:
`count`, `next`, `previous`, `results.entries[]`

## Upload Contract
- Submit attachments as repeated multipart field:
`attachments=<file>`
- Optional text field:
`text=<string>`
- Failure semantics:
  - Validation error -> HTTP `400`
  - Permission error -> HTTP `403`
  - Authentication error -> HTTP `401`
- Retry strategy:
client can retry full request; server writes only on successful transaction.
- Resume:
chunked resume is not implemented in current version.

## Limits (Configurable)
- `ENTRY_MAX_ATTACHMENTS`
- `ENTRY_MAX_FILE_SIZE_MB`
- `ENTRY_ALLOWED_MIME_PREFIXES`
