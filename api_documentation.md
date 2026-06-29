# SplitWiseMoney - REST API Documentation

This document describes the REST API endpoints provided by the SplitWiseMoney expense sharing web application.

All protected API endpoints require JWT authentication. Provide the token in the `Authorization` header as `Bearer <your-token>`.

---

## Authentication APIs

### 1. User Registration
* **Endpoint**: `POST /api/auth/register`
* **Access**: Public
* **Request Body** (`RegisterRequest`):
```json
{
  "fullName": "Rahul Kumar",
  "email": "rahul@test.com",
  "password": "password123"
}
```
* **Validation Rules**:
  - `fullName`: Required, max 100 characters.
  - `email`: Required, must be a valid email format, max 100 characters.
  - `password`: Required, minimum 6 characters.
* **Success Response** (200 OK):
```json
{
  "id": 1,
  "fullName": "Rahul Kumar",
  "email": "rahul@test.com",
  "createdAt": "2026-06-24T20:30:00"
}
```

### 2. User Login
* **Endpoint**: `POST /api/auth/login`
* **Access**: Public
* **Request Body** (`LoginRequest`):
```json
{
  "email": "rahul@test.com",
  "password": "password123"
}
```
* **Validation Rules**:
  - `email`: Required, must be a valid email format.
  - `password`: Required.
* **Success Response** (200 OK):
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer"
}
```

---

## Group Management APIs

### 1. Create Group
* **Endpoint**: `POST /api/groups`
* **Access**: Protected (JWT required)
* **Request Body** (`GroupRequest`):
```json
{
  "groupName": "Goa Trip"
}
```
* **Validation Rules**:
  - `groupName`: Required, max 100 characters.
* **Success Response** (200 OK):
```json
{
  "id": 1,
  "groupName": "Goa Trip",
  "createdById": 1,
  "createdByName": "Rahul Kumar",
  "createdAt": "2026-06-24T20:35:00"
}
```

### 2. List Groups
* **Endpoint**: `GET /api/groups`
* **Access**: Protected
* **Success Response** (200 OK): List of groups the authenticated user is a member of.

### 3. Add Member to Group
* **Endpoint**: `POST /api/groups/{groupId}/members`
* **Access**: Protected
* **Request Body** (`AddMemberRequest`):
```json
{
  "email": "priya@test.com"
}
```
* **Validation Rules**:
  - `email`: Required, valid email format.

---

## Expense APIs

### 1. Add Expense
* **Endpoint**: `POST /api/expenses/group/{groupId}`
* **Access**: Protected
* **Request Body** (`ExpenseRequest`):
```json
{
  "amount": 1200.00,
  "description": "Cab ride to beach",
  "category": "Travel",
  "expenseDate": "2026-06-24",
  "paidById": 1,
  "participantShares": {
    "1": 400.00,
    "2": 400.00,
    "3": 400.00
  }
}
```
* **Validation Rules**:
  - `amount`: Required, min 0.01.
  - `description`: Required, max 255 characters.
  - `category`: Required, max 50 characters.
  - `expenseDate`: Required.
  - `paidById`: Required (must be group member).
  - `participantShares`: Required, non-empty (shares must sum up to amount ± 0.05).

---

## Settlement & Debt Simplification APIs

### 1. Calculate Simplified Debts
* **Endpoint**: `GET /api/settlements/group/{groupId}/owed`
* **Access**: Protected
* **Description**: Runs a greedy settlement simplification algorithm to calculate the minimum transactions required to resolve all balances.
* **Success Response** (200 OK):
```json
[
  {
    "id": null,
    "fromUserId": 3,
    "fromUserName": "Kiran",
    "toUserId": 1,
    "toUserName": "Rahul",
    "amount": 533.33,
    "status": "PENDING",
    "createdAt": null
  },
  {
    "id": null,
    "fromUserId": 3,
    "fromUserName": "Kiran",
    "toUserId": 2,
    "toUserName": "Priya",
    "amount": 133.33,
    "status": "PENDING",
    "createdAt": null
  }
]
```

### 2. Record Settlement Payment
* **Endpoint**: `POST /api/settlements/group/{groupId}`
* **Access**: Protected
* **Request Body** (`SettlementRequest`):
```json
{
  "fromUserId": 3,
  "toUserId": 1,
  "amount": 533.33,
  "status": "SETTLED"
}
```

### 3. Mark Pending Settlement as Settled
* **Endpoint**: `PUT /api/settlements/{settlementId}/settle`
* **Access**: Protected

---

## Dashboard & History APIs

### 1. Get Dashboard Metrics
* **Endpoint**: `GET /api/dashboard`
* **Access**: Protected
* **Success Response** (200 OK): Returns summary cards metrics (total paid, owed, receive) and top 5 recent activities.

### 2. Get Activity History
* **Endpoint**: `GET /api/history`
* **Access**: Protected (Supports pagination, `page`, `size`, `sort`)
