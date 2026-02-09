package com.mind.config;

import com.azure.identity.DeviceCodeCredential;
import com.azure.identity.DeviceCodeCredentialBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphConfig {

    @Value("${azure.client-id}")
    private String clientId;

    @Value("${azure.tenant-id}")
    private String tenantId;

    @Bean
    public GraphServiceClient graphServiceClient() {
        // Create credential using device code flow
        DeviceCodeCredential credential = new DeviceCodeCredentialBuilder()
                .clientId(clientId)
                .tenantId(tenantId)
                .challengeConsumer(challenge -> {
                    // This prints the message to the console for the user to see
                    System.out.println(
                            "\n================================================================================");
                    System.out.println(challenge.getMessage());
                    System.out.println(
                            "================================================================================\n");
                })
                .build();

        // Create and return GraphServiceClient
        return new GraphServiceClient(credential);
    }
}
