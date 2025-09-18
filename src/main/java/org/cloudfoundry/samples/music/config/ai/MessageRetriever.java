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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 * @author Christian Tzolov
 */
public class MessageRetriever {

	@Value("classpath:/prompts/system-qa.st")
	private Resource systemPrompt;

	@Value("classpath:/prompts/question-answer.st")
	private Resource questionAnswerPrompt;
	private VectorStore vectorStore;
	private ChatClient chatClient;

	@Autowired(required = false)
	private ToolCallbackProvider toolCallbackProvider;

	private static final Logger logger = LoggerFactory.getLogger(MessageRetriever.class);

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
			// Step 2: Vector Store Search - Let's see what documents are retrieved
			logger.info("🔥 STEP 2 - VECTOR STORE SEARCH");
			SearchRequest searchRequest = SearchRequest.builder()
				.query(message)
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

			// Create QuestionAnswerAdvisor with custom prompt template that enforces context-only constraint
			PromptTemplate customPromptTemplate = PromptTemplate.builder()
				.resource(questionAnswerPrompt)
				.build();

			QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(this.vectorStore)
				.searchRequest(SearchRequest.builder()
					.similarityThreshold(0.3d)
					.topK(5)
					.build())
				.promptTemplate(customPromptTemplate)
				.build();

			// Log the custom template being used for debugging
			logger.info("🔥 CUSTOM PROMPT TEMPLATE: Using strict context-only template");

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

			// Then add the current user message
			promptSpec = promptSpec.user(message);
			logger.info("🔥 Added user message: '{}'", message);

			if (toolCallbacks.length > 0) {
				promptSpec = promptSpec.toolCallbacks(toolCallbacks);
				logger.info("🔥 Added {} tool callbacks to prompt", toolCallbacks.length);
			}

			logger.info("🔥 STEP 5 - EXECUTING CHAT CALL");
			logger.info("🔥 About to call ChatClient with:");
			logger.info("🔥   - Query: '{}'", message);
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
			logger.info("🔥 Response length: {} characters", response.length());
			logger.info("🔥 Response preview: {}",
				response.length() > 200 ? response.substring(0, 200) + "..." : response);

			logger.info("🔥 ===== RAG PIPELINE DEBUG END =====");
			return response;

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

	private Message getSystemMessage(List<Document> relatedDocuments) {
		String documents = relatedDocuments.stream().map(entry -> entry.getFormattedContent()).collect(Collectors.joining("\n"));
	//	String documents = relatedDocuments.stream().map(entry -> entry.getContent()).collect(Collectors.joining("\n"));
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemPrompt);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("documents", documents));
		return systemMessage;

	}

}
