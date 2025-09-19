/*
 * Copyright 2023-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cloudfoundry.samples.music.config.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.Query;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.List;
import java.util.regex.Pattern;

/**
 *
 * @author Christian Tzolov
 */
public class MessageRetriever {

	@Value("classpath:/prompts/system-qa.st")
	private Resource systemPrompt;
	private VectorStore vectorStore;
	private ChatClient chatClient;

	@Autowired(required = false)
	private ToolCallbackProvider toolCallbackProvider;

	private static final Logger logger = LoggerFactory.getLogger(MessageRetriever.class);

	private static final Pattern THINK_TAG_PATTERN = Pattern.compile("<think>.*?</think>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

	public MessageRetriever(VectorStore vectorStore, ChatClient chatClient) {
		this.vectorStore = vectorStore;
		this.chatClient = chatClient;
	}



	public String retrieve(String message) {
		return retrieve(message, List.of());
	}

	public String retrieve(String message, List<Message> conversationHistory) {
		logger.info("🔥 ===== RAG PIPELINE DEBUG START =====");
		logger.info("🔥 STEP 1 - INITIAL REQUEST");
		logger.info("🔥 Original Query: '{}'", message);
		logger.info("🔥 Conversation History: {} messages", conversationHistory.size());

		if (!conversationHistory.isEmpty()) {
			logger.info("🔥 CONVERSATION CONTEXT:");
			for (int i = 0; i < conversationHistory.size(); i++) {
				Message msg = conversationHistory.get(i);
				String role = msg instanceof UserMessage ? "USER" : "ASSISTANT";
				String content = msg.getText();
				logger.info("🔥   [{}] {}: {}", i + 1, role,
					content.length() > 100 ? content.substring(0, 100) + "..." : content);
			}
		}

		try {
			// Step 2: Query Rewriting - Improve query for better vector search while preserving entity names
			logger.info("🔥 STEP 2 - QUERY REWRITING");

			// Simplified prompt template with structured labels for better tool calling
			String customPromptText = """
				Rewrite this query for better {target} search and tool selection.

				RULES:
				- Use labels: artist: "name", album: "name", track: "name"
				- Fix obvious spelling errors
				- Keep it concise
				- NO explanations or reasoning

				Query: {query}

				Rewritten:""";

			PromptTemplate customPrompt = PromptTemplate.builder()
				.template(customPromptText)
				.build();

			RewriteQueryTransformer queryTransformer = RewriteQueryTransformer.builder()
				.chatClientBuilder(this.chatClient.mutate())
				.promptTemplate(customPrompt)
				.build();

			Query originalQuery = new Query(message);
			Query rewrittenQuery = queryTransformer.transform(originalQuery);
			String optimizedQuery = rewrittenQuery.text().trim();

			logger.info("🔥 Original Query: '{}'", message);
			logger.info("🔥 Rewritten Query: '{}'", optimizedQuery);

			// Step 3: Vector Store Search - Let's see what documents are retrieved
			logger.info("🔥 STEP 3 - VECTOR STORE SEARCH");
			SearchRequest searchRequest = SearchRequest.builder()
				.query(optimizedQuery)
				.similarityThreshold(0.3d)
				.topK(5)
				.build();

			List<Document> retrievedDocs = this.vectorStore.similaritySearch(searchRequest);
			logger.info("🔥 Retrieved {} documents from vector store", retrievedDocs.size());

			for (int i = 0; i < retrievedDocs.size(); i++) {
				Document doc = retrievedDocs.get(i);
				logger.info("🔥   Document [{}]: Score={}, Content Preview: {}",
					i + 1,
					doc.getMetadata().get("distance"),
					doc.getFormattedContent().length() > 150 ? doc.getFormattedContent().substring(0, 150) + "..." : doc.getFormattedContent());
			}

			// Create QuestionAnswerAdvisor with updated system prompt template that enforces tool usage
			PromptTemplate customPromptTemplate = PromptTemplate.builder()
				.resource(systemPrompt)
				.build();

			QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(this.vectorStore)
				.searchRequest(SearchRequest.builder()
					.similarityThreshold(0.3d)
					.topK(5)
					.build())
				.promptTemplate(customPromptTemplate)
				.build();

			// Log the custom template being used for debugging
			logger.info("🔥 SYSTEM PROMPT TEMPLATE: Using updated system-qa.st with mandatory tool usage rules");

			logger.info("🔥 STEP 3 - TOOL CALLBACKS SETUP");
			ToolCallback[] toolCallbacks = toolCallbackProvider != null ? toolCallbackProvider.getToolCallbacks()
				: new ToolCallback[0];

			logger.info("🔥 Available MCP Tools: {} tools", toolCallbacks.length);
			for (int i = 0; i < toolCallbacks.length; i++) {
				ToolCallback tool = toolCallbacks[i];
				String toolInfo = tool.toString();
				logger.info("🔥   Tool [{}]: {}", i + 1,
					toolInfo.length() > 200 ? toolInfo.substring(0, 200) + "..." : toolInfo);
			}

			logger.info("🔥 STEP 4 - BUILDING CHAT PROMPT");
			var promptSpec = this.chatClient
				.prompt()
				.advisors(qaAdvisor);

			// Add conversation history messages first
			if (!conversationHistory.isEmpty()) {
				promptSpec = promptSpec.messages(conversationHistory);
				logger.info("🔥 Added {} conversation history messages to prompt", conversationHistory.size());
			}

			// Then add the current user message (using optimized query for better tool decisions)
			promptSpec = promptSpec.user(optimizedQuery);
			logger.info("🔥 Added user message (optimized): '{}'", optimizedQuery);

			if (toolCallbacks.length > 0) {
				promptSpec = promptSpec.toolCallbacks(toolCallbacks);
				logger.info("🔥 Added {} tool callbacks to prompt", toolCallbacks.length);
			}

			logger.info("🔥 STEP 5 - EXECUTING CHAT CALL");
			logger.info("🔥 About to call ChatClient with:");
			logger.info("🔥   - Original Query: '{}'", message);
			logger.info("🔥   - Optimized Query: '{}'", optimizedQuery);
			logger.info("🔥   - {} retrieved documents", retrievedDocs.size());
			logger.info("🔥   - {} conversation history messages", conversationHistory.size());
			logger.info("🔥   - {} available tools", toolCallbacks.length);

			// Debug tool callback resolution for this specific call
			if (toolCallbacks.length > 0) {
				logger.info("🔥 TOOL CALLBACK DETAILS:");
				for (int i = 0; i < toolCallbacks.length; i++) {
					ToolCallback tool = toolCallbacks[i];
					String toolName = tool.getToolDefinition() != null ? tool.getToolDefinition().name() : "unknown";
					logger.info("🔥   Tool [{}]: Name='{}', Class='{}'",
						i + 1,
						toolName,
						tool.getClass().getSimpleName());
				}
			}

			// Debug conversation history impact on tool calling
			logger.info("🔥 CONVERSATION HISTORY IMPACT ON TOOLS:");
			logger.info("🔥   - Has conversation history: {}", !conversationHistory.isEmpty());
			if (!conversationHistory.isEmpty()) {
				logger.info("🔥   - Last message was from: {}",
					conversationHistory.get(conversationHistory.size() - 1) instanceof UserMessage ? "USER" : "ASSISTANT");
			}

			String response = promptSpec
				.call()
				.content();

			logger.info("🔥 STEP 6 - CHAT RESPONSE RECEIVED");
			logger.info("🔥 Response length: {} characters", response != null ? response.length() : 0);
			logger.info("🔥 Response preview: {}",
				response != null && response.length() > 200 ? response.substring(0, 200) + "..." : response);

			String cleanedResponse = cleanThinkTags(response);

			logger.info("🔥 STEP 7 - CLEANED RESPONSE");
			logger.info("🔥 Cleaned response length: {} characters", cleanedResponse != null ? cleanedResponse.length() : 0);
			logger.info("🔥 Cleaned response preview: {}",
				cleanedResponse != null && cleanedResponse.length() > 200 ? cleanedResponse.substring(0, 200) + "..." : cleanedResponse);

			logger.info("🔥 ===== RAG PIPELINE DEBUG END =====");
			return cleanedResponse;

		} catch (Exception e) {
			logger.error("🔥 ERROR in RAG pipeline", e);
			return "I'm sorry, I encountered an error while processing your question: " + e.getMessage();
		}

		/* // hand rolled implementation
		List<Document> relatedDocuments = this.vectorStore.similaritySearch(message);
		logger.info("first doc retrieved " + relatedDocuments.get(0).toString());

		Message systemMessage = getSystemMessage(relatedDocuments);
		logger.info("system Message retrieved " + systemMessage.toString());

		return this.chatClient.prompt()
				.messages(systemMessage)
				.user(message)
				.call()
				.content();
		*/

	}

	private String cleanThinkTags(String response) {
		if (response == null) {
			return null;
		}

		String cleaned = THINK_TAG_PATTERN.matcher(response).replaceAll("").trim();

		// If the response becomes empty after cleaning, return original
		if (cleaned.isEmpty()) {
			logger.warn("🧹 Think tag cleaning resulted in empty response, returning original");
			return response;
		}

		return cleaned;
	}


}
