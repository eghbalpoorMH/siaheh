# Manual End-to-End Scenario (V2)

1. Register/Login with OTP
- Request OTP with mobile number.
- Verify OTP and receive tokens.

2. Profile onboarding
- If prompted, set `display_name`, `username`, optional `about`, optional avatar.

3. Space lifecycle
- Open personal space.
- Convert personal space to normal space with explicit confirmation.
- Create another space.

4. Members
- Add member by username.
- Discover users from contacts and add one suggested user.
- Update member role and history permission.

5. Entries
- Submit text-only entry.
- Submit entry with multiple attachments.
- Search entries by keyword.
- Filter entries by sender username and date range.

6. Visibility
- Verify owner/admin can see all entries.
- Verify regular member sees only own entries unless role upgraded.

7. Session
- Refresh access token.
- Logout and verify protected endpoints fail without auth.
