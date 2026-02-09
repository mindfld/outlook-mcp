# Outlook MCP Server

This repository contains an MCP (Model Context Protocol) server for Microsoft Outlook, built with Spring Boot and the Microsoft Graph API. It allows AI agents to interact with your emails using **SSE (HTTP) transport**.

## Features

- **Delegated Authentication**: Uses the **Device Code Flow**, allowing you to sign in securely with your own Microsoft account.
- **SSE Transport**: Exposes MCP tools over HTTP, compatible with standard MCP clients.
- **Rich Email Tools**: 
  - `get_recent_emails`: Fetch messages from the last week.
  - `get_email_by_id`: View full message content and metadata.
  - `delete_email_by_id`: Remove unwanted emails.
  - `create_draft_response`: Prepare replies for review.

## Architecture

- **Auth**: Replaces Client Secret with `DeviceCodeCredential` for better personal security.
- **API**: Uses `graphClient.me()` for all actions, ensuring the agent acts as the authenticated user.
- **Spring Boot**: Provides the web server and lifecycle management.

## How to Run

### 1. Azure Portal Setup

To use this server, you need an **App Registration** in the [Azure Portal](https://portal.azure.com):

1.  **Register an App**: Go to "App registrations" > "New registration".
    - Name: `Outlook MCP Server`
    - Supported account types: `Accounts in any organizational directory (Any Microsoft Entra ID tenant - Multitenant) and personal Microsoft accounts (e.g. Skype, Xbox)` (Recommended for personal use).
2.  **API Permissions**: Go to "API permissions" > "Add a permission" > "Microsoft Graph" > **"Delegated permissions"**.
    - Search for and add: `Mail.Read`, `Mail.ReadWrite`, `Mail.Send`.
    - Click "Add permissions".
3.  **Enable Public Client Flows**: Go to "Authentication".
    - Scroll down to "Advanced settings".
    - Set **"Allow public client flows"** to **Yes**. This is required for the Device Code Flow.
    - Click "Save".
4.  **Get IDs**: From the "Overview" tab, copy the **Application (client) ID** and **Directory (tenant) ID**.

### 2. Configure Environment

Set these values as environment variables:
```bash
export AZURE_CLIENT_ID="your-client-id"
export AZURE_TENANT_ID="your-tenant-id"
```

### 2. Build and Start
```bash
mvn clean package
java -jar target/outlook-mcp-1.0-SNAPSHOT.jar
```

### 3. Complete Sign-In (First Run)
When the app starts, follow the instructions in the terminal:
1. Open [https://microsoft.com/devicelogin](https://microsoft.com/devicelogin).
2. Enter the code displayed in the terminal.
3. Sign in with your Microsoft account.

## Connecting an Agent (Technical Details)

The server runs on port **8080** by default and follows the [Model Context Protocol](https://modelcontextprotocol.io/).

### 1. Initiate Session (SSE)
To start a connection, perform a `GET` request to the SSE endpoint. This will establish a persistent stream.
- **Endpoint**: `http://localhost:8080/mcp/sse`

The server will respond with an event containing the `URL` for sending subsequent messages (the "Post URL").

### 2. Initialize the Server
Once you have established the SSE connection, send an `initialize` request via `POST` to:
`http://localhost:8080/mcp/messages`

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {},
    "clientInfo": {
      "name": "my-client",
      "version": "1.0.0"
    }
  }
}
```

### 3. Initialize Notification
After receiving a successful result from the server, you **must** send an `initialized` notification via `POST` to:
`http://localhost:8080/mcp/messages`

**Request:**
```json
{
  "jsonrpc": "2.0",
  "method": "notifications/initialized"
}
```

### 4. Tool Usage Examples

All tool requests should be sent via `POST` to:
`http://localhost:8080/mcp/messages`

#### List Available Tools
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/list"
}
```

#### Call `get_recent_emails`
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "get_recent_emails",
    "arguments": {
      "limit": 5
    }
  }
}
```

#### Call `create_draft_response`
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "tools/call",
  "params": {
    "name": "create_draft_response",
    "arguments": {
      "email_id": "YOUR_EMAIL_ID",
      "body": "Thank you for your email. I will look into this."
    }
  }
}
```
