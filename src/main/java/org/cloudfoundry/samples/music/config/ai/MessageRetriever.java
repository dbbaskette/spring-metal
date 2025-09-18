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
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

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
	private VectorStore vectorStore;
	private ChatClient chatClient;

	private static final Logger logger = LoggerFactory.getLogger(MessageRetriever.class);

	public MessageRetriever(VectorStore vectorStore, ChatClient chatClient) {
		this.vectorStore = vectorStore;
		this.chatClient = chatClient;
	}


	public String retrieve(String message) {
		logger.info("Processing RAG request for message: {}", message);

		try {
			// Create properly configured QuestionAnswerAdvisor
			QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(this.vectorStore)
				.searchRequest(SearchRequest.builder()
					.similarityThreshold(0.3d)
					.topK(5)
					.build())
				.build();

			logger.info("Created QuestionAnswerAdvisor with similarity threshold 0.3 and topK 5");

			// Use the auto-configured ChatClient (already includes MCP tools if available)
			logger.info("Using auto-configured ChatClient with MCP tool integration");
			String response = this.chatClient
				.prompt()
				.advisors(qaAdvisor)
				.user(message)
				.call()
				.content();

			logger.info("RAG response generated successfully, length: {} characters", response.length());
			return response;

		} catch (Exception e) {
			logger.error("Error during RAG processing", e);
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
