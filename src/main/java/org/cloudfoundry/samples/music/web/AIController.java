package org.cloudfoundry.samples.music.web;

import java.util.*;

import org.cloudfoundry.samples.music.config.ai.MessageRetriever;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.cloudfoundry.samples.music.domain.Album;
import org.cloudfoundry.samples.music.domain.MessageRequest;
import org.cloudfoundry.samples.music.domain.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.cloudfoundry.samples.music.service.McpServerConnectionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;


@RestController
@Profile({"llm", "mcp"})
public class AIController {
    private static final Logger logger = LoggerFactory.getLogger(AIController.class);
    private MessageRetriever messageRetriever;
    private VectorStore vectorStore;
    private EmbeddingModel embeddingModel;
    private final McpServerConnectionService connectionService;

    @Autowired(required = false)
    private ToolCallbackProvider toolCallbackProvider;

    public static String generateVectorDoc(Album album) {
            return "artist: " + album.getArtist() + "\n" +
            "title: " + album.getTitle() + "\n" +
            "releaseYear: " + album.getReleaseYear() + "\n" +
            "genre: " + album.getGenre() + "\n" +
            "userReview: " + album.getUserReview() + "\n" +
            "userScore: " + album.getUserScore() + "\n";
    }

    @Autowired
    public AIController(VectorStore vectorStore, MessageRetriever messageRetriever, EmbeddingModel embeddingModel, ObjectProvider<McpServerConnectionService> connectionServiceProvider) {
        this.messageRetriever = messageRetriever;
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.connectionService = connectionServiceProvider.getIfAvailable();
    }
    
    @RequestMapping(value = "/ai/rag", method = RequestMethod.POST)
    public Map<String,Object> generate(@RequestBody MessageRequest messageRequest) {
        Message[] messages = messageRequest.getMessages();
        logger.info("Getting Messages " + messages);

        String query = messages[messages.length - 1].getText();
        String result = messageRetriever.retrieve(query);

        return Map.of("text",result);
    }

    @RequestMapping(value = "/ai/chat", method = RequestMethod.POST)
    public Map<String,Object> chat(@RequestBody Map<String, Object> requestBody) {
        try {
            logger.info("🔄 CHAT REQUEST RECEIVED");
            logger.info("Request keys: {}", requestBody.keySet());
            logger.debug("Full chat request body: {}", requestBody);

            // Extract the message from the deep-chat format
            String message;
            if (requestBody.containsKey("text")) {
                message = (String) requestBody.get("text");
            } else if (requestBody.containsKey("message")) {
                message = (String) requestBody.get("message");
            } else {
                message = requestBody.toString();
            }

            logger.info("💬 PROCESSING MESSAGE: \"{}\"", message);

            // Check for conversation context in request
            boolean hasConversationContext = requestBody.containsKey("conversationContext");
            logger.info("🔍 CONVERSATION CONTEXT CHECK: Request has conversationContext = {}", hasConversationContext);

            if (hasConversationContext) {
                var rawContext = requestBody.get("conversationContext");
                logger.debug("Raw conversationContext type: {}", rawContext != null ? rawContext.getClass().getSimpleName() : "null");
                logger.debug("Raw conversationContext value: {}", rawContext);
            }

            // Extract conversation context from the request
            List<org.springframework.ai.chat.messages.Message> conversationHistory = extractConversationContext(requestBody);

            logger.info("📝 CONTEXT EXTRACTION RESULT: Extracted {} messages for chat processing", conversationHistory.size());

            if (!conversationHistory.isEmpty()) {
                logger.info("✅ CONVERSATION CONTEXT AVAILABLE - Processing with history");
                logger.debug("Conversation history details:");
                for (int i = 0; i < conversationHistory.size(); i++) {
                    var msg = conversationHistory.get(i);
                    String content = msg.getText();
                    String preview = content.length() > 50 ?
                        content.substring(0, 50) + "..." : content;
                    logger.debug("  [{}] {} - \"{}\"", i, msg.getMessageType(), preview);
                }
            } else {
                logger.warn("❌ NO CONVERSATION CONTEXT - Processing without history");
            }

            // Use the MessageRetriever to get AI response with RAG and MCP integration
            logger.info("🤖 CALLING MESSAGE RETRIEVER with message=\"{}\" and {} context messages", message, conversationHistory.size());
            String result = messageRetriever.retrieve(message, conversationHistory);

            logger.info("✅ CHAT RESPONSE GENERATED - Length: {} characters", result.length());
            logger.debug("Response preview: {}", result.length() > 100 ? result.substring(0, 100) + "..." : result);

            // Return in the format expected by deep-chat
            return Map.of("text", result);

        } catch (Exception e) {
            logger.error("❌ ERROR PROCESSING CHAT REQUEST", e);
            return Map.of("text", "I'm sorry, I encountered an error while processing your message: " + e.getMessage());
        }
    }

    @RequestMapping(value = "/ai/addDoc", method = RequestMethod.POST)
    public String addDoc(@RequestBody Album album) {
        String text = generateVectorDoc(album);
        Document doc = new Document(album.getId(), text, new HashMap<>());
        logger.info("Adding Album " + doc.toString());
        this.vectorStore.add(List.of(doc));
        return text;
    }

    @RequestMapping(value = "/ai/deleteDoc", method = RequestMethod.POST)
    public String deleteDoc(@RequestBody String id) {
        logger.info("Deleting Album " + id);
        this.vectorStore.delete(List.of(id));
        return id;
    }

    @RequestMapping(value = "/ai/test-embedding", method = RequestMethod.GET)
    public Map<String, Object> testEmbedding() {
        try {
            logger.info("Testing embedding service connectivity directly...");

            // Test direct embedding operation (bypassing vector store)
            String testText = "This is a test document for embedding";

            logger.info("Creating embedding request for text: {}", testText);
            EmbeddingRequest request = new EmbeddingRequest(List.of(testText), null);

            logger.info("Calling embedding model...");
            EmbeddingResponse response = this.embeddingModel.call(request);

            logger.info("Successfully created embedding, got {} embeddings", response.getResults().size());

            float[] embedding = response.getResults().get(0).getOutput();
            logger.info("Embedding dimensions: {}", embedding.length);

            return Map.of(
                "status", "success",
                "message", "Direct embedding service is working",
                "embeddingDimensions", embedding.length,
                "firstFewValues", Arrays.copyOf(embedding, Math.min(5, embedding.length))
            );

        } catch (Exception e) {
            logger.error("Error testing direct embedding service", e);
            return Map.of(
                "status", "error",
                "message", "Direct embedding service failed: " + e.getMessage(),
                "errorType", e.getClass().getSimpleName()
            );
        }
    }

    @RequestMapping(value = "/ai/test-search", method = RequestMethod.GET)
    public Map<String, Object> testVectorSearch() {
        try {
            logger.info("Testing vector store search...");

            // Test similarity search
            List<org.springframework.ai.document.Document> results =
                this.vectorStore.similaritySearch("test");

            logger.info("Search returned {} documents", results.size());

            return Map.of(
                "status", "success",
                "message", "Vector search is working",
                "documentsFound", results.size(),
                "documents", results.stream().map(doc ->
                    Map.<String, Object>of("id", doc.getId(), "content", doc.getFormattedContent())).toList()
            );

        } catch (Exception e) {
            logger.error("Error testing vector search", e);
            return Map.of(
                "status", "error",
                "message", "Vector search failed: " + e.getMessage(),
                "errorType", e.getClass().getSimpleName()
            );
        }
    }

    @RequestMapping(value = "/ai/populate", method = RequestMethod.POST)
    public Map<String, Object> populateVectorStore() {
        try {
            logger.info("Manual vector store population requested");

            // Add a test document to verify the vector store is working
            List<org.springframework.ai.document.Document> documents = new ArrayList<>();
            documents.add(new org.springframework.ai.document.Document("test-1",
                "artist: Test Artist\ntitle: Test Album\nreleaseYear: 2024\ngenre: Test Genre\nuserReview: Great test album\nuserScore: 5",
                new HashMap<>()));

            this.vectorStore.add(documents);
            logger.info("Added {} test documents to vector store", documents.size());

            // Verify it was added
            List<org.springframework.ai.document.Document> verifyDocs = this.vectorStore.similaritySearch("test");

            return Map.of(
                "status", "success",
                "message", "Added test documents to vector store",
                "documentsAdded", documents.size(),
                "totalDocuments", verifyDocs.size()
            );

        } catch (Exception e) {
            logger.error("Error during manual population", e);
            return Map.of(
                "status", "error",
                "message", e.getMessage()
            );
        }
    }

    private List<org.springframework.ai.chat.messages.Message> extractConversationContext(Map<String, Object> requestBody) {
        List<org.springframework.ai.chat.messages.Message> context = new ArrayList<>();

        try {
            // Extract conversationContext from the request body (sent by frontend)
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> conversationContext = (List<Map<String, Object>>) requestBody.get("conversationContext");

            if (conversationContext != null && !conversationContext.isEmpty()) {
                logger.info("Found conversation context with {} messages in request body", conversationContext.size());
                logger.debug("Raw conversation context: {}", conversationContext);

                for (int i = 0; i < conversationContext.size(); i++) {
                    Map<String, Object> messageData = conversationContext.get(i);
                    String role = (String) messageData.get("role");
                    String content = (String) messageData.get("content");
                    String timestamp = (String) messageData.get("timestamp");

                    logger.debug("Processing context message [{}]: role={}, content_length={}, timestamp={}",
                               i, role, content != null ? content.length() : 0, timestamp);

                    if (role != null && content != null && !content.trim().isEmpty()) {
                        if ("user".equals(role)) {
                            context.add(new org.springframework.ai.chat.messages.UserMessage(content.trim()));
                            logger.debug("Added UserMessage to context: \"{}\"",
                                       content.length() > 50 ? content.substring(0, 50) + "..." : content);
                        } else if ("assistant".equals(role) || "ai".equals(role)) {
                            context.add(new org.springframework.ai.chat.messages.AssistantMessage(content.trim()));
                            logger.debug("Added AssistantMessage to context: \"{}\"",
                                       content.length() > 50 ? content.substring(0, 50) + "..." : content);
                        } else {
                            logger.warn("Unknown message role '{}' in conversation context at index {}", role, i);
                        }
                    } else {
                        logger.warn("Skipping invalid context message at index {}: role={}, content_valid={}",
                                  i, role, content != null && !content.trim().isEmpty());
                    }
                }

                logger.info("Successfully converted {} conversation messages to Spring AI format", context.size());
            } else {
                logger.debug("No conversation context found in request body - conversationContext field is {}",
                           conversationContext == null ? "null" : "empty");
            }
        } catch (Exception e) {
            logger.error("Failed to extract conversation context from request", e);
            logger.debug("Request body keys: {}", requestBody.keySet());
        }

        return context;
    }

    @RequestMapping(value = "/api/ai/context/debug", method = RequestMethod.GET)
    public Map<String, Object> debugContext() {
        try {
            logger.info("🔍 CONTEXT DEBUG ENDPOINT CALLED");

            Map<String, Object> debug = new LinkedHashMap<>();

            // Application state
            debug.put("timestamp", new java.util.Date().toString());
            debug.put("applicationContext", "Spring AI MCP Integration");

            // MessageRetriever state
            Map<String, Object> retrieverInfo = new LinkedHashMap<>();
            retrieverInfo.put("class", messageRetriever.getClass().getSimpleName());
            retrieverInfo.put("vectorStoreAvailable", vectorStore != null);
            retrieverInfo.put("embeddingModelAvailable", embeddingModel != null);
            debug.put("messageRetriever", retrieverInfo);

            // MCP Tool information
            Map<String, Object> mcpInfo = new LinkedHashMap<>();
            if (toolCallbackProvider != null) {
                try {
                    var callbacks = toolCallbackProvider.getToolCallbacks();
                    mcpInfo.put("toolCallbackProviderAvailable", true);
                    mcpInfo.put("totalToolCallbacks", callbacks.length);

                    List<Map<String, Object>> toolDetails = new ArrayList<>();
                    for (var callback : callbacks) {
                        Map<String, Object> toolDetail = new LinkedHashMap<>();
                        toolDetail.put("className", callback.getClass().getSimpleName());
                        toolDetail.put("string", callback.toString());
                        toolDetails.add(toolDetail);
                    }
                    mcpInfo.put("availableTools", toolDetails);
                } catch (Exception e) {
                    mcpInfo.put("toolCallbackProviderError", e.getMessage());
                }
            } else {
                mcpInfo.put("toolCallbackProviderAvailable", false);
            }
            debug.put("mcpIntegration", mcpInfo);

            // MCP Connection status
            Map<String, Object> connectionInfo = new LinkedHashMap<>();
            if (connectionService != null) {
                try {
                    var connections = connectionService.listConnections();
                    connectionInfo.put("connectionServiceAvailable", true);
                    connectionInfo.put("totalConnections", connections.size());

                    List<Map<String, Object>> connectionDetails = new ArrayList<>();
                    for (var conn : connections) {
                        Map<String, Object> connDetail = new LinkedHashMap<>();
                        connDetail.put("id", conn.getId() != null ? conn.getId().toString() : null);
                        connDetail.put("name", conn.getName());
                        connDetail.put("baseUrl", conn.getBaseUrl());
                        connDetail.put("endpoint", conn.getEndpoint());
                        connDetail.put("enabled", conn.isEnabled());
                        connDetail.put("status", conn.getStatus() != null ? conn.getStatus().name() : "UNKNOWN");
                        connDetail.put("toolCount", conn.getToolCount());
                        connDetail.put("lastSuccessfulAt", conn.getLastSuccessfulAt());
                        connDetail.put("lastFailureAt", conn.getLastFailureAt());
                        connDetail.put("lastErrorMessage", conn.getLastErrorMessage());
                        connectionDetails.add(connDetail);
                    }
                    connectionInfo.put("connections", connectionDetails);
                } catch (Exception e) {
                    connectionInfo.put("connectionServiceError", e.getMessage());
                }
            } else {
                connectionInfo.put("connectionServiceAvailable", false);
            }
            debug.put("connectionService", connectionInfo);

            // Vector store information
            Map<String, Object> vectorInfo = new LinkedHashMap<>();
            if (vectorStore != null) {
                try {
                    vectorInfo.put("vectorStoreClass", vectorStore.getClass().getSimpleName());
                    // Test vector search
                    var testResults = vectorStore.similaritySearch("test");
                    vectorInfo.put("testSearchResults", testResults.size());
                    vectorInfo.put("vectorStoreWorking", true);
                } catch (Exception e) {
                    vectorInfo.put("vectorStoreError", e.getMessage());
                    vectorInfo.put("vectorStoreWorking", false);
                }
            } else {
                vectorInfo.put("vectorStoreAvailable", false);
            }
            debug.put("vectorStore", vectorInfo);

            // System information
            Map<String, Object> systemInfo = new LinkedHashMap<>();
            systemInfo.put("javaVersion", System.getProperty("java.version"));
            systemInfo.put("springProfilesActive", System.getProperty("spring.profiles.active"));
            systemInfo.put("availableProcessors", Runtime.getRuntime().availableProcessors());
            systemInfo.put("maxMemory", Runtime.getRuntime().maxMemory());
            systemInfo.put("freeMemory", Runtime.getRuntime().freeMemory());
            debug.put("systemInfo", systemInfo);

            // Current thread information
            Map<String, Object> threadInfo = new LinkedHashMap<>();
            threadInfo.put("currentThread", Thread.currentThread().getName());
            threadInfo.put("activeThreadCount", Thread.activeCount());
            debug.put("threadInfo", threadInfo);

            logger.info("✅ CONTEXT DEBUG ENDPOINT - Generated debug info with {} top-level sections", debug.size());
            return debug;

        } catch (Exception e) {
            logger.error("❌ ERROR IN CONTEXT DEBUG ENDPOINT", e);
            return Map.of(
                "status", "error",
                "message", "Error generating debug information: " + e.getMessage(),
                "errorType", e.getClass().getSimpleName(),
                "timestamp", new java.util.Date().toString()
            );
        }
    }

    @RequestMapping(value = "/api/ai/process-flow", method = RequestMethod.GET)
    public Map<String, Object> getProcessFlow() {
        try {
            logger.info("📋 PROCESS FLOW DOCUMENTATION ENDPOINT CALLED");

            Map<String, Object> flow = new LinkedHashMap<>();

            flow.put("title", "Spring Metal AI Chat Process Flow");
            flow.put("description", "Complete documentation of query processing from frontend to backend and response generation");
            flow.put("timestamp", new java.util.Date().toString());

            // Frontend Process Flow
            Map<String, Object> frontend = new LinkedHashMap<>();
            List<Map<String, Object>> frontendSteps = new ArrayList<>();

            frontendSteps.add(Map.of(
                "step", 1,
                "component", "User Interface (Angular.js + deep-chat)",
                "action", "User enters message in chat interface",
                "details", "Message captured by deep-chat web component"
            ));

            frontendSteps.add(Map.of(
                "step", 2,
                "component", "ChatController (mcp.js)",
                "action", "Request interceptor triggered",
                "details", "Injects conversation context from localStorage into request body",
                "location", "src/main/resources/static/js/mcp.js:544-592"
            ));

            frontendSteps.add(Map.of(
                "step", 3,
                "component", "Context Management",
                "action", "Add conversationContext array to request",
                "details", "Last 6 messages from conversation history sent to backend",
                "storageKey", "spring-metal-conversation-context"
            ));

            frontendSteps.add(Map.of(
                "step", 4,
                "component", "HTTP Request",
                "action", "POST to /ai/chat endpoint",
                "details", "Request body contains 'text' and 'conversationContext' fields"
            ));

            frontend.put("description", "Frontend conversation context management and request preparation");
            frontend.put("steps", frontendSteps);
            flow.put("frontend", frontend);

            // Backend Process Flow
            Map<String, Object> backend = new LinkedHashMap<>();
            List<Map<String, Object>> backendSteps = new ArrayList<>();

            backendSteps.add(Map.of(
                "step", 1,
                "component", "AIController.chat()",
                "action", "Receive HTTP POST request",
                "details", "Extract message text and conversationContext from request body",
                "location", "src/main/java/org/cloudfoundry/samples/music/web/AIController.java:65-127"
            ));

            backendSteps.add(Map.of(
                "step", 2,
                "component", "Context Extraction",
                "action", "Parse conversation history",
                "details", "Convert frontend context to Spring AI Message objects (UserMessage/AssistantMessage)",
                "location", "AIController.extractConversationContext():243-293"
            ));

            backendSteps.add(Map.of(
                "step", 3,
                "component", "MessageRetriever",
                "action", "Process RAG request with context",
                "details", "Calls retrieve(message, conversationHistory) with both current query and conversation history",
                "location", "src/main/java/org/cloudfoundry/samples/music/config/ai/MessageRetriever.java:67-132"
            ));

            backendSteps.add(Map.of(
                "step", 4,
                "component", "Vector Store (RAG)",
                "action", "Similarity search for relevant documents",
                "details", "QuestionAnswerAdvisor searches vector store using similarity threshold 0.3 and topK 5",
                "location", "MessageRetriever:73-78"
            ));

            backendSteps.add(Map.of(
                "step", 5,
                "component", "MCP Tool Integration",
                "action", "Gather available MCP tools",
                "details", "ToolCallbackProvider provides dynamic tools from connected MCP servers",
                "location", "MessageRetriever:84-87"
            ));

            backendSteps.add(Map.of(
                "step", 6,
                "component", "ChatClient (Spring AI)",
                "action", "Build and execute prompt",
                "details", "Combines: 1) Conversation history, 2) Current message, 3) RAG context, 4) MCP tools",
                "location", "MessageRetriever:89-108"
            ));

            backendSteps.add(Map.of(
                "step", 7,
                "component", "AI Model Processing",
                "action", "Generate response using OpenAI/LLM",
                "details", "Model processes full context: history + RAG + tools to generate contextually aware response"
            ));

            backendSteps.add(Map.of(
                "step", 8,
                "component", "Response Generation",
                "action", "Return JSON response",
                "details", "AIController returns Map.of('text', result) to frontend",
                "location", "AIController:121"
            ));

            backend.put("description", "Backend RAG processing with conversation context and MCP tool integration");
            backend.put("steps", backendSteps);
            flow.put("backend", backend);

            // Follow-up Query Process
            Map<String, Object> followUp = new LinkedHashMap<>();
            List<String> followUpProcess = new ArrayList<>();
            followUpProcess.add("User asks follow-up question (e.g., 'What albums have they released?' after asking about Genesis)");
            followUpProcess.add("Frontend maintains conversation context in localStorage and sessionStorage");
            followUpProcess.add("Request interceptor automatically includes previous conversation in conversationContext array");
            followUpProcess.add("Backend receives BOTH the follow-up question AND the conversation history");
            followUpProcess.add("Spring AI ChatClient processes the full conversation context");
            followUpProcess.add("AI model understands 'they' refers to 'Genesis' from previous context");
            followUpProcess.add("Response is contextually appropriate and maintains conversation continuity");

            followUp.put("description", "How follow-up queries maintain context");
            followUp.put("process", followUpProcess);
            flow.put("followUpQueries", followUp);

            // Component Integration
            Map<String, Object> integration = new LinkedHashMap<>();
            Map<String, Object> components = new LinkedHashMap<>();

            components.put("deep-chat", Map.of(
                "role", "Web component for chat UI",
                "responsibility", "Message display, user input, event handling"
            ));

            components.put("mcp.js (ChatController)", Map.of(
                "role", "Frontend state management",
                "responsibility", "Context persistence, request interception, navigation handling"
            ));

            components.put("AIController", Map.of(
                "role", "REST API endpoint",
                "responsibility", "Request processing, context extraction, response formatting"
            ));

            components.put("MessageRetriever", Map.of(
                "role", "RAG processing coordinator",
                "responsibility", "Vector search, context assembly, AI model interaction"
            ));

            components.put("QuestionAnswerAdvisor", Map.of(
                "role", "Spring AI RAG component",
                "responsibility", "Document retrieval and context injection"
            ));

            components.put("ToolCallbackProvider", Map.of(
                "role", "MCP tool integration",
                "responsibility", "Dynamic tool discovery and registration from MCP servers"
            ));

            components.put("VectorStore", Map.of(
                "role", "PostgreSQL with pgvector",
                "responsibility", "Semantic search for relevant album/artist information"
            ));

            integration.put("components", components);
            flow.put("integration", integration);

            // Data Flow
            Map<String, Object> dataFlow = new LinkedHashMap<>();
            List<String> dataSteps = new ArrayList<>();
            dataSteps.add("User message → Frontend state → Request with context → Backend processing");
            dataSteps.add("Context extraction → RAG document retrieval → MCP tool gathering → AI processing");
            dataSteps.add("AI response → JSON formatting → Frontend update → UI display → Context storage");

            dataFlow.put("description", "Data transformation through the system");
            dataFlow.put("flow", dataSteps);
            flow.put("dataFlow", dataFlow);

            // Error Handling
            Map<String, Object> errorHandling = new LinkedHashMap<>();
            errorHandling.put("frontendErrors", List.of(
                "Navigation context loss - handled by localStorage restoration",
                "Request interception failures - logged with detailed debugging",
                "Deep-chat component issues - graceful degradation"
            ));
            errorHandling.put("backendErrors", List.of(
                "MCP connection failures - retry logic with exponential backoff",
                "Vector store unavailable - graceful degradation without RAG",
                "Context parsing errors - fallback to message-only processing"
            ));
            flow.put("errorHandling", errorHandling);

            logger.info("✅ PROCESS FLOW DOCUMENTATION - Generated comprehensive documentation with {} sections", flow.size());
            return flow;

        } catch (Exception e) {
            logger.error("❌ ERROR IN PROCESS FLOW DOCUMENTATION", e);
            return Map.of(
                "status", "error",
                "message", "Error generating process flow documentation: " + e.getMessage(),
                "timestamp", new java.util.Date().toString()
            );
        }
    }

    @RequestMapping(value = "/api/mcp/status", method = RequestMethod.GET)
    public Map<String, Object> getMcpStatus() {
        List<Map<String, Object>> servers = new ArrayList<>();
        if (connectionService != null) {
            connectionService.listConnections().forEach(connection -> {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("id", connection.getId() != null ? connection.getId().toString() : null);
                summary.put("name", connection.getName());
                summary.put("baseUrl", connection.getBaseUrl());
                summary.put("endpoint", connection.getEndpoint());
                summary.put("enabled", connection.isEnabled());
                summary.put("status", connection.getStatus() != null ? connection.getStatus().name() : "UNKNOWN");
                summary.put("lastSuccessfulAt", connection.getLastSuccessfulAt());
                summary.put("lastFailureAt", connection.getLastFailureAt());
                summary.put("lastErrorMessage", connection.getLastErrorMessage());
                if (connection.getDefaultHeaders() != null && !connection.getDefaultHeaders().isEmpty()) {
                    summary.put("headers", connection.getDefaultHeaders());
                }
                servers.add(summary);
            });
        }

        boolean hasActiveServer = servers.stream()
                .anyMatch(server -> Boolean.TRUE.equals(server.get("enabled"))
                        && "ACTIVE".equals(server.get("status")));

        boolean toolsExposed = toolCallbackProvider != null && hasActiveServer;

        // Calculate actual tool count from active servers
        int totalToolCount = 0;
        if (toolsExposed && toolCallbackProvider != null) {
            try {
                totalToolCount = toolCallbackProvider.getToolCallbacks().length;
            } catch (Exception e) {
                // Fallback to sum from server entities if ToolCallbackProvider fails
                totalToolCount = servers.stream()
                    .filter(server -> Boolean.TRUE.equals(server.get("enabled"))
                                   && "ACTIVE".equals(server.get("status")))
                    .mapToInt(server -> {
                        Object toolCountObj = server.get("toolCount");
                        return toolCountObj instanceof Integer ? (Integer) toolCountObj : 0;
                    })
                    .sum();
            }
        }

        return Map.of(
                "enabled", toolsExposed,
                "message", toolsExposed ? "MCP tools are available via dynamic registration"
                        : "No active MCP servers registered",
                "toolCount", totalToolCount,
                "servers", servers
        );
    }
}
