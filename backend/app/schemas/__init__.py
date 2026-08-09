from datetime import date, datetime

from pydantic import BaseModel, EmailStr, Field


class TokenPair(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class ParentRegister(BaseModel):
    email: EmailStr
    password: str = Field(min_length=6)
    display_name: str = Field(min_length=1, max_length=120)
    family_name: str = Field(default="Mening oilam", max_length=120)


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


class ChildPairRequest(BaseModel):
    pairing_code: str = Field(min_length=6, max_length=6)
    display_name: str = Field(min_length=1, max_length=120)
    device_name: str = Field(default="Android", max_length=120)
    chat_pin: str = Field(min_length=4, max_length=8)


class UserOut(BaseModel):
    id: int
    email: str
    display_name: str
    role: str
    family_id: int | None

    model_config = {"from_attributes": True}


class FamilyOut(BaseModel):
    id: int
    name: str
    pairing_code: str

    model_config = {"from_attributes": True}


class AuthResponse(BaseModel):
    user: UserOut
    family: FamilyOut | None = None
    tokens: TokenPair
    device_id: int | None = None


class MessageCreate(BaseModel):
    body: str = Field(min_length=1, max_length=4000)


class MessageOut(BaseModel):
    id: int
    family_id: int
    sender_id: int
    sender_name: str | None = None
    body: str
    created_at: datetime

    model_config = {"from_attributes": True}


class UsageItem(BaseModel):
    package_name: str
    app_label: str
    total_ms: int
    day: date


class UsageSyncRequest(BaseModel):
    device_id: int
    items: list[UsageItem]


class SmsItem(BaseModel):
    address: str
    body: str
    direction: str = "inbox"
    received_at: datetime


class SmsSyncRequest(BaseModel):
    device_id: int
    items: list[SmsItem]


class NotificationItem(BaseModel):
    package_name: str
    title: str | None = None
    text: str | None = None
    posted_at: datetime


class NotificationSyncRequest(BaseModel):
    device_id: int
    items: list[NotificationItem]


class HeartbeatRequest(BaseModel):
    device_id: int
    wifi_ssid: str | None = None


class DeviceOut(BaseModel):
    id: int
    device_name: str
    wifi_ssid: str | None
    last_seen_at: datetime | None
    is_online: bool
    child_user_id: int

    model_config = {"from_attributes": True}


class DashboardSummary(BaseModel):
    family: FamilyOut
    devices: list[DeviceOut]
    top_apps_today: list[UsageItem]
    unread_hint: int = 0


class FcmTokenUpdate(BaseModel):
    fcm_token: str


class PinVerify(BaseModel):
    pin: str


class MediaOut(BaseModel):
    id: int
    filename: str
    content_type: str
    url: str
    taken_at: datetime | None
    created_at: datetime
