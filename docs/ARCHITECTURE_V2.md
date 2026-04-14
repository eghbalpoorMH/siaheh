# Siaheh V2 Architecture Decisions

## Domain and Naming
- Product is a team logbook/event recorder with messenger-like UX.
- UI term for entry is `ثبت`.
- Backend storage currently keeps `Message` model for compatibility, mapped to UI `ثبت`.
- Hybrid wording is intentionally avoided for now; UI stays on `ثبت` terminology.

## Identity and Privacy
- User public profile: `username`, `display_name`, `avatar`, `about`.
- User private credential: `phone` only for auth and recovery.
- Public APIs do not return other users' phone number.
- Member and entry sender payloads now expose `user` public profile object.

## Spaces and Personal Mode
- Spaces now have `kind`:
`personal` or `space`.
- Personal space can be converted to normal space only by owner and only through explicit confirmation.
- Conversion is irreversible by API policy.

## Entries and Attachments
- Entry timeline is backed by `Message` model.
- Multiple attachments supported with dedicated `MessageAttachment` model.
- Supported attachment kinds:
`image`, `video`, `audio`, `document`, `file`.
- Legacy `image` field migration is preserved via data migration to attachment records.
- Deferred roadmap decision (documented, not implemented): reply/quote/forward/pin on entries.

## API Contract
- Username-based member add is supported (`username` or `user_id`).
- User search endpoint exists for username/display-name discovery.
- Contact discovery endpoint exists for finding existing users from phone list.
- Entries listing is paginated with page/page_size.
- Messages and entries endpoints both exist for compatibility.

## Upload Strategy
- Current strategy: direct multipart upload through backend.
- Future-ready strategy: presigned object-storage upload can be added without breaking entry model.

## Visibility Rules
- `owner`/`admin`/`view_all` can read all entries in space.
- `member` reads own entries unless role/policy upgraded.
- Membership soft-remove keeps historical data.

## Android App Layer
- Space/member views consume public-profile-first payloads.
- Entry composer supports multi-file selection and multipart upload.
- Members flow supports username-based add and contact-based discovery.
- New profile setup screen is shown when display name is missing after OTP login.
