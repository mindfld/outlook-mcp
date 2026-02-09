package co.susko;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.json.McpJsonMapper;
import jakarta.servlet.http.HttpServlet;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

@SpringBootApplication
public class OutlookMcpApplication {

        public static void main(String[] args) {
                SpringApplication.run(OutlookMcpApplication.class, args);
        }

        @Bean
        public ServletRegistrationBean<HttpServlet> mcpServlet() {
                // Create the server with HTTP SSE transport using correct builder methods
                HttpServletSseServerTransportProvider transport = HttpServletSseServerTransportProvider.builder()
                                .sseEndpoint("/sse")
                                .messageEndpoint("/messages")
                                .build();

                var server = McpServer.sync(transport)
                                .serverInfo("outlook-mcp", "1.0.0")
                                .capabilities(McpSchema.ServerCapabilities.builder()
                                                .tools(true)
                                                .logging()
                                                .build())
                                .tool(new Tool(
                                                "get_recent_emails",
                                                "Get recent emails from Outlook",
                                                "Get recent emails from Outlook",
                                                new McpSchema.JsonSchema(
                                                                "object",
                                                                Map.of(),
                                                                List.of(),
                                                                Boolean.FALSE,
                                                                Map.of(),
                                                                Map.of()),
                                                null, // outputSchema
                                                null, // annotations
                                                Map.of() // meta
                                ),
                                                (exchange, arguments) -> {
                                                        // Mock implementation
                                                        String emailData = "[{\"subject\": \"Welcome to MCP\", \"from\": \"sender@example.com\", \"body\": \"This is a test email.\"}]";

                                                        return new CallToolResult(
                                                                        List.of(new TextContent(emailData)),
                                                                        false);
                                                })
                                .build();

                // Register the transport servlet
                ServletRegistrationBean<HttpServlet> servletBean = new ServletRegistrationBean<>(transport, "/mcp/*");
                return servletBean;
        }
}
