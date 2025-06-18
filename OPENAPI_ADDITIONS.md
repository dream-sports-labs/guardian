# OpenAPI Specification Additions for OIDC Client Management

This document summarizes the additions made to the Guardian OpenAPI specification (`src/main/resources/oas/guardian.yaml`) for the OIDC Client Management features.

## New API Endpoints Added

### Client Management Endpoints

#### 1. `/v1/admin/client` (POST)
- **Purpose**: Create a new OIDC client
- **Tags**: Client Management
- **Request Body**: `CreateClientRequest`
- **Responses**: 
  - `201`: Client created successfully (`ClientResponse`)
  - `400`: Bad Request (validation errors)
  - `409`: Conflict (client name already exists)
  - `500`: Internal Server Error

#### 2. `/v1/admin/client` (GET)
- **Purpose**: List OIDC clients with pagination
- **Tags**: Client Management
- **Query Parameters**: 
  - `page` (optional): Page number (0-based)
  - `size` (optional): Items per page (1-100, default 20)
- **Responses**:
  - `200`: Clients retrieved successfully (`ClientListResponse`)
  - `400`: Bad Request (invalid pagination)
  - `500`: Internal Server Error

#### 3. `/v1/admin/client/{clientId}` (GET)
- **Purpose**: Get specific OIDC client by ID
- **Tags**: Client Management
- **Path Parameters**: `clientId` (UUID)
- **Responses**:
  - `200`: Client retrieved successfully (`ClientResponse`)
  - `404`: Client not found
  - `500`: Internal Server Error

#### 4. `/v1/admin/client/{clientId}` (PATCH)
- **Purpose**: Update existing OIDC client
- **Tags**: Client Management
- **Path Parameters**: `clientId` (UUID)
- **Request Body**: `UpdateClientRequest`
- **Responses**:
  - `200`: Client updated successfully (`ClientResponse`)
  - `400`: Bad Request (validation errors)
  - `404`: Client not found
  - `500`: Internal Server Error

#### 5. `/v1/admin/client/{clientId}` (DELETE)
- **Purpose**: Delete OIDC client and all associated scopes
- **Tags**: Client Management
- **Path Parameters**: `clientId` (UUID)
- **Responses**:
  - `204`: Client deleted successfully
  - `404`: Client not found
  - `500`: Internal Server Error

### Client-Scope Management Endpoints

#### 6. `/v1/admin/client/{clientId}/scope` (POST)
- **Purpose**: Add scope to OIDC client
- **Tags**: Client Scope Management
- **Path Parameters**: `clientId` (UUID)
- **Request Body**: `CreateClientScopeRequest`
- **Responses**:
  - `201`: Scope added successfully (`ClientScopeResponse`)
  - `400`: Bad Request (invalid data)
  - `404`: Client or scope not found
  - `409`: Conflict (scope already assigned)
  - `500`: Internal Server Error

#### 7. `/v1/admin/client/{clientId}/scope` (GET)
- **Purpose**: List scopes assigned to client with pagination
- **Tags**: Client Scope Management
- **Path Parameters**: `clientId` (UUID)
- **Query Parameters**: 
  - `page` (optional): Page number (0-based)
  - `size` (optional): Items per page (1-100, default 20)
- **Responses**:
  - `200`: Client scopes retrieved successfully (`ClientScopeListResponse`)
  - `400`: Bad Request (invalid pagination)
  - `404`: Client not found
  - `500`: Internal Server Error

#### 8. `/v1/admin/client/{clientId}/scope/{scopeId}` (DELETE)
- **Purpose**: Remove scope from OIDC client
- **Tags**: Client Scope Management
- **Path Parameters**: 
  - `clientId` (UUID)
  - `scopeId` (UUID)
- **Responses**:
  - `204`: Scope removed successfully
  - `404`: Client, scope, or assignment not found
  - `500`: Internal Server Error

### Scope Management Endpoints

#### 9. `/v1/admin/scope` (POST)
- **Purpose**: Create a new OAuth2/OIDC scope
- **Tags**: Scope Management
- **Request Body**: `CreateScopeRequest`
- **Responses**:
  - `201`: Scope created successfully (`ScopeResponse`)
  - `400`: Bad Request (validation errors)
  - `409`: Conflict (scope name already exists)
  - `500`: Internal Server Error

#### 10. `/v1/admin/scope` (GET)
- **Purpose**: List OAuth2/OIDC scopes with pagination and search
- **Tags**: Scope Management
- **Query Parameters**: 
  - `page` (optional): Page number (0-based)
  - `size` (optional): Items per page (1-100, default 20)
  - `search` (optional): Search term to filter by name
- **Responses**:
  - `200`: Scopes retrieved successfully (`ScopeListResponse`)
  - `400`: Bad Request (invalid pagination)
  - `500`: Internal Server Error

#### 11. `/v1/admin/scope/{scopeId}` (GET)
- **Purpose**: Get specific OAuth2/OIDC scope by ID
- **Tags**: Scope Management
- **Path Parameters**: `scopeId` (UUID)
- **Responses**:
  - `200`: Scope retrieved successfully (`ScopeResponse`)
  - `404`: Scope not found
  - `500`: Internal Server Error

#### 12. `/v1/admin/scope/{scopeId}` (PATCH)
- **Purpose**: Update existing OAuth2/OIDC scope
- **Tags**: Scope Management
- **Path Parameters**: `scopeId` (UUID)
- **Request Body**: `UpdateScopeRequest`
- **Responses**:
  - `200`: Scope updated successfully (`ScopeResponse`)
  - `400`: Bad Request (validation errors)
  - `404`: Scope not found
  - `500`: Internal Server Error

#### 13. `/v1/admin/scope/{scopeId}` (DELETE)
- **Purpose**: Delete OAuth2/OIDC scope and remove from all clients
- **Tags**: Scope Management
- **Path Parameters**: `scopeId` (UUID)
- **Responses**:
  - `204`: Scope deleted successfully
  - `404`: Scope not found
  - `500`: Internal Server Error

## New Schema Definitions Added

### Request Schemas

#### `CreateClientRequest`
- **Required Fields**: `clientName`, `clientUri`
- **Optional Fields**: `contacts`, `grantTypes`, `responseTypes`, `redirectUris`, `logoUri`, `policyUri`, `skipConsent`
- **Validation**: 
  - `clientName`: Max 255 characters
  - `clientUri`: Valid URI format
  - `contacts`: Array of valid email addresses
  - `grantTypes`: Enum values (`authorization_code`, `client_credentials`, `refresh_token`)
  - `responseTypes`: Enum values (`code`, `token`)
  - `redirectUris`: Array of valid URIs

#### `UpdateClientRequest`
- **All Fields Optional**: Same as `CreateClientRequest` but all fields are optional for partial updates

#### `CreateScopeRequest`
- **Required Fields**: `name`, `description`
- **Validation**:
  - `name`: Pattern `^[a-zA-Z0-9._:-]+$`, max 100 characters
  - `description`: Max 1000 characters

#### `UpdateScopeRequest`
- **All Fields Optional**: Same as `CreateScopeRequest` but all fields are optional

#### `CreateClientScopeRequest`
- **Required Fields**: `scopeId`
- **Validation**: `scopeId` must be valid UUID format

### Response Schemas

#### `ClientResponse`
- **Fields**: `tenantId`, `clientId`, `clientName`, `clientSecret`, `clientUri`, `contacts`, `grantTypes`, `responseTypes`, `redirectUris`, `logoUri`, `policyUri`, `skipConsent`
- **Notes**: Includes generated `clientId` (UUID) and `clientSecret`

#### `ClientListResponse`
- **Fields**: `clients` (array), `totalCount`, `page`, `size`
- **Pagination Support**: Standard pagination response format

#### `ScopeResponse`
- **Fields**: `id`, `tenantId`, `name`, `description`
- **Notes**: Includes generated `id` (UUID)

#### `ScopeListResponse`
- **Fields**: `scopes` (array), `totalCount`, `page`, `size`
- **Pagination Support**: Standard pagination response format

#### `ClientScopeResponse`
- **Fields**: `tenantId`, `clientId`, `scopeId`, `scopeName`, `scopeDescription`
- **Notes**: Includes scope details for convenience

#### `ClientScopeListResponse`
- **Fields**: `scopes` (array), `totalCount`, `page`, `size`
- **Pagination Support**: Standard pagination response format

## Key Features of the API Design

### 1. **Consistent REST Design**
- Standard HTTP methods (GET, POST, PATCH, DELETE)
- Consistent URL patterns
- Proper HTTP status codes

### 2. **Comprehensive Validation**
- Required field validation
- Format validation (URI, email, UUID)
- Enum validation for OAuth2 values
- Length limits for text fields

### 3. **Pagination Support**
- Consistent pagination across list endpoints
- Configurable page size with limits
- Total count information

### 4. **Search Functionality**
- Search by scope name
- Case-insensitive pattern matching

### 5. **Multi-tenant Support**
- All endpoints require `tenant-id` header
- Tenant isolation enforced at API level

### 6. **OAuth2/OIDC Compliance**
- Standard grant types and response types
- Proper client credential management
- Scope-based authorization model

### 7. **Error Handling**
- Comprehensive error responses
- Specific error codes for different scenarios
- Consistent error response format

## Usage Examples

### Create a Client
```bash
curl -X POST /v1/admin/client \
  -H "tenant-id: tenant1" \
  -H "Content-Type: application/json" \
  -d '{
    "clientName": "My OAuth2 App",
    "clientUri": "https://myapp.example.com",
    "grantTypes": ["authorization_code", "refresh_token"],
    "responseTypes": ["code"],
    "redirectUris": ["https://myapp.example.com/callback"]
  }'
```

### Create a Scope
```bash
curl -X POST /v1/admin/scope \
  -H "tenant-id: tenant1" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "user.read",
    "description": "Read access to user information"
  }'
```

### Assign Scope to Client
```bash
curl -X POST /v1/admin/client/{clientId}/scope \
  -H "tenant-id: tenant1" \
  -H "Content-Type: application/json" \
  -d '{
    "scopeId": "550e8400-e29b-41d4-a716-446655440001"
  }'
```

This comprehensive API specification provides a complete interface for managing OIDC clients and scopes in the Guardian authentication system.
