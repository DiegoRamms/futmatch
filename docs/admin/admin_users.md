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

Returns a paginated list of all users whose current role is `ADMIN` or `ORGANIZER`. The list includes accounts in every user status, including `ACTIVE`, `BLOCKED`, and `SUSPENDED`.

- **Method:** `GET`
- **Path:** `/admin/users`
- **Required role:** `ADMIN`

### Query Parameters

| Parameter | Type | Required | Default | Rules |
|:--|:--|:--|:--|:--|
| `page` | Int | No | `1` | Must be between `1` and `1000`. |
| `pageSize` | Int | No | `20` | Must be between `1` and `100`. |

### Request Example

```http
GET /admin/users?page=1&pageSize=20
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
| `items` | Array | Privileged users for the requested page. |
| `items[].profilePic` | String? | Signed URL for the profile image, or `null` when none exists. |
| `items[].role` | Enum | `ADMIN` or `ORGANIZER`. |
| `items[].status` | Enum | `ACTIVE`, `BLOCKED`, or `SUSPENDED`. |
| `page` | Int | Returned page number. |
| `pageSize` | Int | Maximum number of records requested. |
| `total` | Long | Total number of users with role `ADMIN` or `ORGANIZER`. |

---

## 2. Update Organizer Access

Changes the role, status, or both for an existing organizer account. At least one field is required.

- **Method:** `PATCH`
- **Path:** `/admin/users/{userId}/access`
- **Required role:** `ADMIN`

### Path Parameters

| Parameter | Type | Description |
|:--|:--|:--|
| `userId` | UUID | Identifier of the organizer to update. |

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
- Only accounts whose current role is `ORGANIZER` can be updated.
- Existing `ADMIN` accounts and `PLAYER` accounts cannot be updated through this endpoint.
- When the access data changes, all active refresh tokens for the target user are revoked with reason `ADMIN_REVOCATION`.
- Access tokens already issued remain valid until their expiration time.

### Error Cases

| HTTP Status | Scenario |
|:--|:--|
| `400 Bad Request` | Pagination is invalid or neither `role` nor `status` was provided. |
| `403 Forbidden` | The caller is not an admin, is trying to update themselves, or the target is not an organizer. |
| `404 Not Found` | `userId` is invalid or no matching manageable user exists. |
