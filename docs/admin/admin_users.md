# Admin Users API

This document describes the administrative endpoints for listing privileged users and managing organizer access.

## Common Concepts

### Authentication

All endpoints require an access token with the `ADMIN` role.

- **Required header:** `Authorization: Bearer <access_token>`

### Responses

Responses use the standard application wrapper.

- **Success:** `{ "data": { ... }, "error": null }`
- **Failure:** `{ "data": null, "error": { ... } }`

### Localization

Use the `Accept-Language` header to receive localized validation and error messages, for example `en-US` or `es-MX`.

---

## 1. List Admins and Organizers

Returns a paginated list of users filtered by role and optional status. Deleted/anonymized accounts are always excluded. By default, it returns accounts with the `ADMIN` or `ORGANIZER` role in every remaining status.

- **Method:** `GET`
- **Path:** `/admin/users`
- **Required role:** `ADMIN`

### Query Parameters

| Parameter | Type | Required | Default | Rules |
|:--|:--|:--|:--|:--|
| `page` | Int | No | `1` | Must be between `1` and `1000`. |
| `pageSize` | Int | No | `20` | Must be between `1` and `100`. |
| `roles` | Comma-separated Enum | No | `ADMIN,ORGANIZER` | Roles to include: `PLAYER`, `ADMIN`, `ORGANIZER`. Example: `roles=PLAYER`. |
| `statuses` | Comma-separated Enum | No | All statuses | Statuses to include: `ACTIVE`, `BLOCKED`, `SUSPENDED`. Example: `statuses=ACTIVE,BLOCKED`. |

### Request Example

```http
GET /admin/users?roles=PLAYER&statuses=ACTIVE&page=1&pageSize=20
Authorization: Bearer <access_token>
Accept-Language: es-MX
```

### Success Response Example

```json
{
  "data": {
    "items": [
      {
        "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
        "name": "Ana",
        "lastName": "López",
        "email": "ana.lopez@example.com",
        "phone": "+525512345678",
        "country": "MX",
        "birthDate": 631152000000,
        "gender": "FEMALE",
        "profilePic": "https://res.cloudinary.com/.../avatar.jpg",
        "role": "ORGANIZER",
        "status": "ACTIVE",
        "isEmailVerified": true,
        "createdAt": 1715436000000
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1
  },
  "error": null
}
```

### Response Fields

| Field | Type | Description |
|:--|:--|:--|
| `items` | Array | Users matching the requested filters for the requested page. |
| `items[].profilePic` | String? | Signed URL for the profile image, or `null` when none exists. |
| `items[].role` | Enum | `PLAYER`, `ADMIN`, or `ORGANIZER`. |
| `items[].status` | Enum | `ACTIVE`, `BLOCKED`, or `SUSPENDED`. |
| `page` | Int | Returned page number. |
| `pageSize` | Int | Maximum number of records requested. |
| `total` | Long | Total number of users matching the filters, not the total across all users. |

---

## 2. Get User Details

Returns the detail data required by the administrative user profile. It includes account, security, participation, and device data in one request.

- **Method:** `GET`
- **Path:** `/admin/users/{userId}`
- **Required role:** `ADMIN`

### Success Response Example

```json
{
  "data": {
    "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "name": "Ana",
    "lastName": "López",
    "email": "ana.lopez@example.com",
    "phone": "+525512345678",
    "country": "MX",
    "birthDate": 631152000000,
    "gender": "FEMALE",
    "profilePic": "https://res.cloudinary.com/.../avatar.jpg",
    "role": "PLAYER",
    "status": "ACTIVE",
    "isEmailVerified": true,
    "createdAt": 1715436000000,
    "account": {
      "emailVerifiedAt": 1715436000000,
      "accessUpdatedAt": null
    },
    "security": {
      "activeSessionCount": 2,
      "failedLoginAttempts": 0,
      "lockedUntil": null
    },
    "participation": {
      "upcomingMatchesCount": 1,
      "completedMatchesCount": 14
    },
    "devices": [
      {
        "id": "b1b2c3d4-e5f6-7890-1234-567890abcdef",
        "platform": "DESKTOP",
        "deviceInfo": "MacBook Pro",
        "appVersion": "1.0.0",
        "osVersion": "macOS 15.6",
        "isTrusted": true,
        "isActive": true,
        "lastUsedAt": 1715439600000,
        "createdAt": 1715436000000
      }
    ]
  },
  "error": null
}
```

`emailVerifiedAt` and `accessUpdatedAt` can be `null` for historical accounts whose event predates tracking. `activeSessionCount` counts unexpired active refresh tokens. Participation counts include reserved or joined matches; upcoming matches have `SCHEDULED` or `IN_PROGRESS` status and completed matches have `COMPLETED` status.

## 3. Get User Payment History

- **Method:** `GET`
- **Path:** `/admin/users/{userId}/payment-history?page=1`
- **Required role:** `ADMIN`

Returns five payment attempts per page, ordered by the latest status change. Each item includes the field name, match start time, amount in cents, payment status, payment/refund timestamps, and the masked card details when Stripe has supplied them. No provider identifiers or payment secrets are returned.

Card details are read from the local database first. For missing details on Stripe payments, the backend retrieves the payment's expanded latest charge and caches only the brand and last four digits. Recovery is limited to the requested page (up to five concurrent requests), with connection/read timeouts and no automatic network retries. Provider or cache failures do not prevent returning payment history. A payment without card details returns a null method and can be checked again on a later request. Historical cards are recovered on demand; no bulk migration is performed. Webhooks also expand the latest charge to populate new payments. Card caching does not change payment status, amounts, dates, or page ordering.

```json
{
  "data": {
    "items": [{
      "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
      "fieldName": "Cancha Roma Norte",
      "matchStartsAt": 1715436000000,
      "amountInCents": 12000,
      "currency": "MXN",
      "status": "SUCCEEDED",
      "statusUpdatedAt": 1715436000000,
      "paidAt": 1715436000000,
      "method": { "brand": "visa", "last4": "4242" },
      "refundedAt": null
    }],
    "page": 1,
    "pageSize": 5,
    "total": 14
  },
  "error": null
}
```

## 4. Update Organizer Access

Changes the role, status, or both for an existing account. At least one field is required.

- **Method:** `PATCH`
- **Path:** `/admin/users/{userId}/access`
- **Required role:** `ADMIN`

### Path Parameters

| Parameter | Type | Description |
|:--|:--|:--|
| `userId` | UUID | Identifier of the user to update. |

### Request Body

```json
{
  "role": "ADMIN",
  "status": "ACTIVE"
}
```

### Request Fields

| Field | Type | Required | Valid Values |
|:--|:--|:--|:--|
| `role` | Enum | No | `PLAYER`, `ADMIN`, `ORGANIZER` |
| `status` | Enum | No | `ACTIVE`, `BLOCKED`, `SUSPENDED` |

At least one of `role` or `status` must be present. Omitting a field preserves its current value.

### Success Response Example

```json
{
  "data": true,
  "error": null
}
```

### Access Rules

- An administrator cannot update their own administrative access through this endpoint.
- Accounts with any current role can be updated.
- The last active administrator cannot be blocked, suspended, or demoted.
- When the access data changes, all active refresh tokens for the target user are revoked with reason `ADMIN_REVOCATION`.
- Access tokens already issued remain valid until their expiration time.

### Error Cases

| HTTP Status | Scenario |
|:--|:--|
| `400 Bad Request` | Pagination is invalid or neither `role` nor `status` was provided. |
| `403 Forbidden` | The caller is not an admin, is trying to update themselves, or the change would remove the last active admin. |
| `404 Not Found` | `userId` is invalid or no matching manageable user exists. |

---

## 5. User Deletion Preview and Deletion

- **Preview:** `GET /admin/users/{userId}/deletion-preview`
- **Delete:** `DELETE /admin/users/{userId}`
- **Required role:** `ADMIN`

The preview returns the target's name, email, role, status, `canDelete`, and a technical `blockReason` when applicable. This lets the client show the admin exactly which account is about to be affected.

Deletion requires the current administrator password:

```json
{ "password": "current-admin-password" }
```

The endpoint rejects self-deletion, inactive accounts, the last active administrator, and users that organize or participate in a scheduled or in-progress match. On success it runs the same anonymization, credential revocation, and profile-image cleanup flow as self-deletion.
