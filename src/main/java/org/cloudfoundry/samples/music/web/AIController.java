package org.cloudfoundry.samples.music.web;

import java.util.*;

import org.cloudfoundry.samples.music.config.ai.MessageRetriever;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;


@RestController
@Profile("llm")
public class AIController {
    private static final Logger logger = LoggerFactory.getLogger(AIController.class);
    private MessageRetriever messageRetriever;
    private VectorStore vectorStore;
    private EmbeddingModel embeddingModel;

    public static String generateVectorDoc(Album album) {
            return "artist: " + album.getArtist() + "\n" +
            "title: " + album.getTitle() + "\n" +
            "releaseYear: " + album.getReleaseYear() + "\n" +
            "genre: " + album.getGenre() + "\n" +
            "userReview: " + album.getUserReview() + "\n" +
            "userScore: " + album.getUserScore() + "\n";
    }

    @Autowired
    public AIController(VectorStore vectorStore, MessageRetriever messageRetriever, EmbeddingModel embeddingModel) {
        this.messageRetriever = messageRetriever;
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }
    
    @RequestMapping(value = "/ai/rag", method = RequestMethod.POST)
    public Map<String,Object> generate(@RequestBody MessageRequest messageRequest) {
        Message[] messages = messageRequest.getMessages();
        logger.info("Getting Messages " + messages);

        String query = messages[messages.length - 1].getText();
        String result = messageRetriever.retrieve(query);

        return Map.of("text",result);
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
}
