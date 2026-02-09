package com.mind;

import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.MessageCollectionResponse;
import com.microsoft.graph.users.item.messages.item.createreply.CreateReplyPostRequestBody;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import jakarta.servlet.http.HttpServlet;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootApplication
public class OutlookMcpApplication {

        public static void main(String[] args) {
                SpringApplication.run(OutlookMcpApplication.class, args);
        }

        @Bean
        public ServletRegistrationBean<HttpServlet> mcpServlet(GraphServiceClient graphClient) {
                // Create the server with HTTP Streamable transport
                HttpServletStreamableServerTransportProvider transport = HttpServletStreamableServerTransportProvider
                                .builder()
                                .mcpEndpoint("")
                                .build();

                McpServer.SyncSpecification<?> serverSpec = McpServer.sync(transport)
                                .serverInfo("outlook-mcp", "1.0.0")
                                .capabilities(McpSchema.ServerCapabilities.builder()
                                                .tools(true)
                                                .logging()
                                                .build());

                // Register tools
                registerTools(serverSpec, graphClient);

                // Build and register the transport servlet
                serverSpec.build();
                ServletRegistrationBean<HttpServlet> servletBean = new ServletRegistrationBean<>(transport, "/mcp/*");
                return servletBean;
        }

        private void registerTools(McpServer.SyncSpecification<?> serverSpec, GraphServiceClient graphClient) {
                serverSpec
                                // Tool 1: Get recent emails (last week)
                                .toolCall(new Tool(
                                                "get_recent_emails",
                                                "Get recent emails from the last week",
                                                "Retrieves emails from Outlook that are not older than 1 week",
                                                new McpSchema.JsonSchema(
                                                                "object",
                                                                Map.of(
                                                                                "limit", Map.of(
                                                                                                "type", "number",
                                                                                                "description",
                                                                                                "Maximum number of emails to return (default: 10)")),
                                                                List.of(),
                                                                Boolean.FALSE,
                                                                Map.of(),
                                                                Map.of()),
                                                null,
                                                null,
                                                Map.of()),
                                                (McpSyncServerExchange exchange, CallToolRequest request) -> {
                                                        try {
                                                                final int limit = request.arguments()
                                                                                .containsKey("limit")
                                                                                                ? ((Number) request
                                                                                                                .arguments()
                                                                                                                .get("limit"))
                                                                                                                .intValue()
                                                                                                : 100;

                                                                OffsetDateTime oneWeekAgo = OffsetDateTime.now()
                                                                                .minusWeeks(1);
                                                                String filterDate = oneWeekAgo.format(
                                                                                DateTimeFormatter.ISO_OFFSET_DATE_TIME);

                                                                MessageCollectionResponse messages = graphClient.me()
                                                                                .messages()
                                                                                .get(requestConfig -> {
                                                                                        requestConfig.queryParameters.filter = "receivedDateTime ge "
                                                                                                        + filterDate;
                                                                                        requestConfig.queryParameters.top = limit;
                                                                                        requestConfig.queryParameters.orderby = new String[] {
                                                                                                        "receivedDateTime DESC" };
                                                                                        requestConfig.queryParameters.select = new String[] {
                                                                                                        "id", "subject",
                                                                                                        "from",
                                                                                                        "receivedDateTime",
                                                                                                        "bodyPreview" };
                                                                                });

                                                                List<Message> messageList = messages.getValue();
                                                                String emailData = messageList.stream()
                                                                                .map(msg -> String.format(
                                                                                                "{\"id\":\"%s\",\"subject\":\"%s\",\"from\":\"%s\",\"date\":\"%s\",\"bodyPreview\":\"%s\"}",
                                                                                                msg.getId(),
                                                                                                escapeJson(msg.getSubject()),
                                                                                                msg.getFrom() != null
                                                                                                                && msg.getFrom().getEmailAddress() != null
                                                                                                                                ? escapeJson(msg.getFrom()
                                                                                                                                                .getEmailAddress()
                                                                                                                                                .getAddress())
                                                                                                                                : "unknown",
                                                                                                msg.getReceivedDateTime(),
                                                                                                escapeJson(msg.getBodyPreview())))
                                                                                .collect(Collectors.joining(",", "[",
                                                                                                "]"));

                                                                return new CallToolResult(
                                                                                List.of(new TextContent(emailData)),
                                                                                false,
                                                                                null,
                                                                                Map.of());
                                                        } catch (Exception e) {
                                                                return new CallToolResult(
                                                                                List.of(new TextContent(
                                                                                                "Error fetching emails: "
                                                                                                                + e.getMessage())),
                                                                                true,
                                                                                null,
                                                                                Map.of());
                                                        }
                                                })

                                // Tool 2: Get email by ID
                                .toolCall(new Tool(
                                                "get_email_by_id",
                                                "Get full email details by ID",
                                                "Retrieves complete email information including full body for a specific email ID",
                                                new McpSchema.JsonSchema(
                                                                "object",
                                                                Map.of(
                                                                                "email_id", Map.of(
                                                                                                "type", "string",
                                                                                                "description",
                                                                                                "The unique identifier of the email")),
                                                                List.of("email_id"),
                                                                Boolean.FALSE,
                                                                Map.of(),
                                                                Map.of()),
                                                null,
                                                null,
                                                Map.of()),
                                                (McpSyncServerExchange exchange, CallToolRequest request) -> {
                                                        try {
                                                                String emailId = (String) request.arguments()
                                                                                .get("email_id");

                                                                Message message = graphClient.me()
                                                                                .messages()
                                                                                .byMessageId(emailId)
                                                                                .get();

                                                                String toAddresses = message.getToRecipients() != null
                                                                                ? message.getToRecipients().stream()
                                                                                                .map(r -> "\"" + escapeJson(
                                                                                                                r.getEmailAddress()
                                                                                                                                .getAddress())
                                                                                                                + "\"")
                                                                                                .collect(Collectors
                                                                                                                .joining(",", "[",
                                                                                                                                "]"))
                                                                                : "[]";

                                                                String emailData = String.format(
                                                                                "{\"id\":\"%s\",\"subject\":\"%s\",\"from\":\"%s\",\"to\":%s,\"date\":\"%s\",\"body\":\"%s\",\"hasAttachments\":%b}",
                                                                                message.getId(),
                                                                                escapeJson(message.getSubject()),
                                                                                message.getFrom() != null && message
                                                                                                .getFrom()
                                                                                                .getEmailAddress() != null
                                                                                                                ? escapeJson(message
                                                                                                                                .getFrom()
                                                                                                                                .getEmailAddress()
                                                                                                                                .getAddress())
                                                                                                                : "unknown",
                                                                                toAddresses,
                                                                                message.getReceivedDateTime(),
                                                                                escapeJson(message.getBody() != null
                                                                                                ? message.getBody()
                                                                                                                .getContent()
                                                                                                : ""),
                                                                                message.getHasAttachments() != null
                                                                                                ? message.getHasAttachments()
                                                                                                : false);

                                                                return new CallToolResult(
                                                                                List.of(new TextContent(emailData)),
                                                                                false,
                                                                                null,
                                                                                Map.of());
                                                        } catch (Exception e) {
                                                                return new CallToolResult(
                                                                                List.of(new TextContent(
                                                                                                "Error fetching email: "
                                                                                                                + e.getMessage())),
                                                                                true,
                                                                                null,
                                                                                Map.of());
                                                        }
                                                })

                                // Tool 3: Create draft response
                                .toolCall(new Tool(
                                                "create_draft_response",
                                                "Create a draft reply to an email",
                                                "Creates a draft response to a specific email that can be reviewed and sent later",
                                                new McpSchema.JsonSchema(
                                                                "object",
                                                                Map.of(
                                                                                "email_id", Map.of(
                                                                                                "type", "string",
                                                                                                "description",
                                                                                                "The unique identifier of the email to reply to"),
                                                                                "body", Map.of(
                                                                                                "type", "string",
                                                                                                "description",
                                                                                                "The body content of the draft reply")),
                                                                List.of("email_id", "body"),
                                                                Boolean.FALSE,
                                                                Map.of(),
                                                                Map.of()),
                                                null,
                                                null,
                                                Map.of()),
                                                (McpSyncServerExchange exchange, CallToolRequest request) -> {
                                                        try {
                                                                String emailId = (String) request.arguments()
                                                                                .get("email_id");
                                                                String body = (String) request.arguments().get("body");

                                                                CreateReplyPostRequestBody createReplyPostRequestBody = new CreateReplyPostRequestBody();
                                                                Message draft = graphClient.me()
                                                                                .messages()
                                                                                .byMessageId(emailId)
                                                                                .createReply()
                                                                                .post(createReplyPostRequestBody);

                                                                Message updateMessage = new Message();
                                                                com.microsoft.graph.models.ItemBody itemBody = new com.microsoft.graph.models.ItemBody();
                                                                itemBody.setContentType(
                                                                                com.microsoft.graph.models.BodyType.Text);
                                                                itemBody.setContent(body);
                                                                updateMessage.setBody(itemBody);

                                                                graphClient.me()
                                                                                .messages()
                                                                                .byMessageId(draft.getId())
                                                                                .patch(updateMessage);

                                                                String result = String.format(
                                                                                "{\"success\":true,\"draftId\":\"%s\",\"inReplyTo\":\"%s\",\"subject\":\"%s\",\"body\":\"%s\",\"createdAt\":\"%s\"}",
                                                                                draft.getId(),
                                                                                emailId,
                                                                                escapeJson(draft.getSubject()),
                                                                                escapeJson(body),
                                                                                java.time.Instant.now().toString());

                                                                return new CallToolResult(
                                                                                List.of(new TextContent(result)),
                                                                                false,
                                                                                null,
                                                                                Map.of());
                                                        } catch (Exception e) {
                                                                return new CallToolResult(
                                                                                List.of(new TextContent(
                                                                                                "Error creating draft: "
                                                                                                                + e.getMessage())),
                                                                                true,
                                                                                null,
                                                                                Map.of());
                                                        }
                                                })
                                // Tool 4: Delete email by ID
                                .toolCall(new Tool(
                                                "delete_email_by_id",
                                                "Delete an email by ID",
                                                "Permanently deletes an email from Outlook by its unique identifier",
                                                new McpSchema.JsonSchema(
                                                                "object",
                                                                Map.of(
                                                                                "email_id", Map.of(
                                                                                                "type", "string",
                                                                                                "description",
                                                                                                "The unique identifier of the email to delete")),
                                                                List.of("email_id"),
                                                                Boolean.FALSE,
                                                                Map.of(),
                                                                Map.of()),
                                                null,
                                                null,
                                                Map.of()),
                                                (McpSyncServerExchange exchange, CallToolRequest request) -> {
                                                        try {
                                                                String emailId = (String) request.arguments()
                                                                                .get("email_id");

                                                                // Delete email via Graph API
                                                                graphClient.me()
                                                                                .messages()
                                                                                .byMessageId(emailId)
                                                                                .delete();

                                                                String result = String.format(
                                                                                "{\"success\":true,\"message\":\"Email %s has been deleted successfully\",\"deletedAt\":\"%s\"}",
                                                                                emailId,
                                                                                java.time.Instant.now().toString());

                                                                return new CallToolResult(
                                                                                List.of(new TextContent(result)),
                                                                                false,
                                                                                null,
                                                                                Map.of());
                                                        } catch (Exception e) {
                                                                return new CallToolResult(
                                                                                List.of(new TextContent(
                                                                                                "Error deleting email: "
                                                                                                                + e.getMessage())),
                                                                                true,
                                                                                null,
                                                                                Map.of());
                                                        }
                                                });
        }

        @Bean
        public CommandLineRunner authTrigger(GraphServiceClient graphClient) {
                return args -> {
                        System.out.println("\nInitializing Microsoft Graph connection...");
                        try {
                                // This call will trigger the Device Code Flow challenge if not authenticated
                                graphClient.me().get();
                                System.out.println("Graph connection initialized successfully.");
                        } catch (Exception e) {
                                // We don't want to crash the app if auth fails (e.g. timeout),
                                // but the user should see the error.
                                System.err.println("Note: Initial Graph connection failed: " + e.getMessage());
                        }
                };
        }

        // Helper method to escape JSON strings
        private static String escapeJson(String value) {
                if (value == null)
                        return "";
                return value.replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n")
                                .replace("\r", "\\r")
                                .replace("\t", "\\t");
        }
}
