from django.urls import reverse
from django.core.files.uploadedfile import SimpleUploadedFile
from rest_framework import status
from rest_framework.test import APITestCase

from apps.accounts.models import User
from apps.entries.models import Message
from apps.spaces.models import Space, SpaceMembership


class AccountsPrivacyAndMembershipTests(APITestCase):
    def setUp(self):
        self.owner = User.objects.create_user(phone="09120000001", username="owner001", display_name="Owner")
        self.member = User.objects.create_user(phone="09120000002", username="member001", display_name="Member")
        self.space = Space.objects.create(
            title="Test Space",
            description="",
            owner=self.owner,
            kind=Space.KIND_SPACE,
        )
        SpaceMembership.objects.create(
            space=self.space,
            user=self.owner,
            role=SpaceMembership.ROLE_OWNER,
            is_active=True,
            can_read_history=True,
        )
        SpaceMembership.objects.create(
            space=self.space,
            user=self.member,
            role=SpaceMembership.ROLE_MEMBER,
            is_active=True,
            can_read_history=True,
        )
        Message.objects.create(space=self.space, sender=self.owner, text="hello")
        Message.objects.create(space=self.space, sender=self.member, text="mine")

    def test_message_list_hides_phone(self):
        self.client.force_authenticate(self.member)
        url = reverse("space-messages", kwargs={"space_id": self.space.id})
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        payload = response.data["results"]["entries"][0]
        self.assertIn("sender", payload)
        self.assertNotIn("sender_phone", payload)
        self.assertNotIn("phone", payload["sender"])

    def test_add_member_by_username(self):
        new_user = User.objects.create_user(phone="09120000003", username="newuser01")
        self.client.force_authenticate(self.owner)
        url = reverse("space-member-add", kwargs={"space_id": self.space.id})
        response = self.client.post(url, {"username": new_user.username}, format="json")
        self.assertIn(response.status_code, (status.HTTP_200_OK, status.HTTP_201_CREATED))
        self.assertEqual(response.data["user"]["username"], "newuser01")

    def test_discover_contacts_returns_public_user_only(self):
        self.client.force_authenticate(self.owner)
        url = reverse("discover-contacts")
        response = self.client.post(url, {"phones": ["09120000002"]}, format="json")
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertTrue(response.data["users"])
        user = response.data["users"][0]
        self.assertEqual(user["username"], "member001")
        self.assertNotIn("phone", user)

    def test_convert_personal_space_requires_owner(self):
        personal = Space.objects.create(
            title="شخصی",
            description="",
            owner=self.owner,
            kind=Space.KIND_PERSONAL,
        )
        SpaceMembership.objects.create(
            space=personal,
            user=self.owner,
            role=SpaceMembership.ROLE_OWNER,
            is_active=True,
            can_read_history=True,
        )
        self.client.force_authenticate(self.owner)
        url = reverse("space-convert", kwargs={"space_id": personal.id})
        response = self.client.post(url, {"confirm": True}, format="json")
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        personal.refresh_from_db()
        self.assertEqual(personal.kind, Space.KIND_SPACE)

    def test_member_visibility_only_own_entries(self):
        self.client.force_authenticate(self.member)
        url = reverse("space-messages", kwargs={"space_id": self.space.id})
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        entries = response.data["results"]["entries"]
        self.assertEqual(len(entries), 1)
        self.assertEqual(entries[0]["text"], "mine")

    def test_entry_search_filter(self):
        self.client.force_authenticate(self.owner)
        url = reverse("space-messages", kwargs={"space_id": self.space.id})
        response = self.client.get(url, {"search": "hell"})
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        entries = response.data["results"]["entries"]
        self.assertEqual(len(entries), 1)
        self.assertEqual(entries[0]["text"], "hello")

    def test_non_admin_cannot_list_members(self):
        outsider = User.objects.create_user(phone="09120000009", username="outsider01")
        SpaceMembership.objects.create(
            space=self.space,
            user=outsider,
            role=SpaceMembership.ROLE_MEMBER,
            is_active=True,
            can_read_history=True,
        )
        self.client.force_authenticate(outsider)
        url = reverse("space-member-list", kwargs={"space_id": self.space.id})
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def test_create_entry_with_attachment(self):
        self.client.force_authenticate(self.owner)
        upload = SimpleUploadedFile("note.txt", b"hello attachment", content_type="text/plain")
        url = reverse("space-messages", kwargs={"space_id": self.space.id})
        response = self.client.post(url, {"text": "with file", "attachments": [upload]}, format="multipart")
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertTrue(response.data["attachments"])

    def test_profile_update_flow(self):
        self.client.force_authenticate(self.owner)
        url = reverse("profile")
        response = self.client.patch(url, {"display_name": "مالک", "username": "ownernew01"}, format="json")
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["display_name"], "مالک")
        self.assertEqual(response.data["username"], "ownernew01")


class AuthRegressionTests(APITestCase):
    def test_request_otp_works(self):
        url = reverse("otp-request")
        response = self.client.post(url, {"phone": "09121234567"}, format="json")
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertIn("expires_in", response.data)

    def test_invalid_refresh_token_returns_401(self):
        url = reverse("token")
        response = self.client.put(url, {"refresh_token": "invalid.token.value"}, format="json")
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)
