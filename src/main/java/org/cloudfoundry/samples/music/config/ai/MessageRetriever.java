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
		logger.info("🔥 ===== RAG PIPELINE START =====");
		logger.info("🔥 Query: '{}'", message);
		if (!conversationHistory.isEmpty()) {
			logger.info("🔥 Context: {} messages", conversationHistory.size());
		}

		try {
			// Step 2: Query Rewriting
			logger.info("🔥 STEP 2 - QUERY REWRITING");

			// Enhanced prompt template for better tool calling
			String customPromptText = """
				Rewrite this query for better {target} search and tool selection.

				RULES:
				- Preserve the intent and meaning of the question (e.g., "who is in" means band members, "what albums" means discography)
				- Add context words that help with search (e.g., "band members", "discography", "biography")
				- Fix obvious spelling errors (e.g., "nirvanas" -> "Nirvana")
				- Make the query clear and specific for tool calling
				- Keep the natural question format if it's a question
				- NO explanations, just the rewritten query

				Examples:
				"list nirvanas albums" -> "What albums are by Nirvana?"
				"who is in metallica" -> "Who are the band members of Metallica?"
				"songs on nevermind" -> "What tracks are on the album Nevermind by Nirvana?"

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
			String rawOptimizedQuery = rewrittenQuery.text().trim();

			// Clean any think tags from the rewritten query
			String optimizedQuery = cleanThinkTags(rawOptimizedQuery);

			logger.info("🔥 Rewritten: '{}'", optimizedQuery);

			// Step 3: Vector Store Search
			logger.info("🔥 STEP 3 - VECTOR SEARCH");
			SearchRequest searchRequest = SearchRequest.builder()
				.query(optimizedQuery)
				.similarityThreshold(0.3d)
				.topK(5)
				.build();

			List<Document> retrievedDocs = this.vectorStore.similaritySearch(searchRequest);
			logger.info("🔥 Retrieved {} documents", retrievedDocs.size());

			// Step 4: Setup tools for LLM
			logger.info("🔥 STEP 4 - TOOL SETUP");
			ToolCallback[] toolCallbacks = toolCallbackProvider != null ? toolCallbackProvider.getToolCallbacks()
				: new ToolCallback[0];
			logger.info("🔧 Available tools: {}", toolCallbacks.length);

			// Create QuestionAnswerAdvisor
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

			// Step 5: Build prompt and let LLM call tools
			logger.info("🔥 STEP 5 - BUILDING PROMPT WITH TOOLS");
			var promptSpec = this.chatClient
				.prompt()
				.advisors(qaAdvisor);

			// Add conversation history messages first
			if (!conversationHistory.isEmpty()) {
				promptSpec = promptSpec.messages(conversationHistory);
			}

			// Then add the current user message (using optimized query for better tool decisions)
			promptSpec = promptSpec.user(optimizedQuery);

			logger.info("🔥 STEP 6 - EXECUTING CHAT CALL");

			// Debug: Check if tools are actually available in the ChatClient
			try {
				logger.info("🔍 ChatClient debug info:");
				logger.info("🔍 ChatClient class: {}", this.chatClient.getClass().getSimpleName());

				// Check if we can get tool info from the prompt spec
				var chatRequestBuilder = promptSpec.call();
				logger.info("🔍 Chat request built successfully");

			} catch (Exception e) {
				logger.warn("🔍 Could not debug ChatClient: {}", e.getMessage());
			}

			// Execute the call and capture the full response for debugging
			var chatResponse = promptSpec.call();
			String response = chatResponse.content();

			// Debug: Log the raw response to understand what's happening
			logger.info("🔍 Raw LLM response: '{}'", response != null ? response.substring(0, Math.min(response.length(), 500)) : "null");

			// Debug: Check if there were any tool calls attempted
			try {
				var result = chatResponse.chatResponse();
				if (result != null && result.getResults() != null && !result.getResults().isEmpty()) {
					var generation = result.getResults().get(0);
					logger.info("🔍 Generation metadata: {}", generation.getMetadata());

					// Check for tool calls in the generation
					if (generation.getMetadata() != null && generation.getMetadata().containsKey("finish-reason")) {
						Object finishReason = generation.getMetadata().get("finish-reason");
						logger.info("🔍 Finish reason: {}", String.valueOf(finishReason));
					}
				}
			} catch (Exception e) {
				logger.warn("🔍 Could not inspect chat response: {}", e.getMessage());
			}

			String cleanedResponse = cleanThinkTags(response);
			logger.info("🔥 RESPONSE: {} chars", cleanedResponse != null ? cleanedResponse.length() : 0);
			logger.info("🔥 ===== RAG PIPELINE END =====");
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
