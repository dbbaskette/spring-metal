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

import org.cloudfoundry.samples.music.web.AIController;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.cloudfoundry.samples.music.domain.Album;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.CrudRepository;

import org.springframework.core.annotation.Order;

/**
 *
 * @author Christian Tzolov
 */
@Order(2)
public class VectorStoreInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(VectorStoreInitializer.class);

    private final VectorStore vectorStore;

    @Autowired
    private CrudRepository<Album, String> albumRepository;

    public VectorStoreInitializer(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // Run vector store initialization asynchronously to prevent blocking startup
        initializeAsync();
    }

    @Async
    public void initializeAsync() {
        try {
            logger.info("=== Vector Store Initialization Starting ===");

            // Check if we have albums in the database
            long albumCount = albumRepository.count();
            logger.info("Found {} albums in database", albumCount);

            if (albumCount == 0) {
                logger.warn("No albums found in database - vector store will be empty");
                return;
            }

            // Skip the similarity search check for now since it's hanging
            // and just proceed with population
            logger.info("Proceeding directly to vector store population...");
            populateVectorStore();

        } catch (Exception e) {
            logger.error("Error during vector store initialization", e);
        }
    }

    public void populateVectorStore() {
        try {
            logger.info("Starting vector store population...");

            Iterable<Album> albums = albumRepository.findAll();
            List<Document> documents = new ArrayList<>();

            int count = 0;
            for (Album album : albums) {
                try {
                    logger.debug("Processing album: {} - {}", album.getArtist(), album.getTitle());
                    String albumDoc = AIController.generateVectorDoc(album);
                    documents.add(new Document(album.getId(), albumDoc, new HashMap<>()));
                    count++;
                } catch (Exception e) {
                    logger.error("Error preparing document for album {}: {}", album.getId(), e.getMessage());
                }
            }

            logger.info("Prepared {} documents for vector store", count);

            if (!documents.isEmpty()) {
                // Start with just ONE document to test if embedding works at all
                logger.info("Testing with single document first...");
                List<Document> singleDoc = List.of(documents.get(0));

                try {
                    logger.info("Attempting to add single test document...");
                    this.vectorStore.add(singleDoc);
                    logger.info("SUCCESS: Single document added successfully!");

                    // If that works, process the rest in batches
                    logger.info("Proceeding with remaining documents in batches...");
                    int batchSize = 3; // Smaller batches
                    for (int i = 1; i < documents.size(); i += batchSize) {
                        int endIndex = Math.min(i + batchSize, documents.size());
                        List<Document> batch = documents.subList(i, endIndex);

                        logger.info("Adding batch of {} documents (documents {}-{})...",
                            batch.size(), i + 1, endIndex);

                        try {
                            this.vectorStore.add(batch);
                            logger.info("Successfully added batch of {} documents", batch.size());
                            Thread.sleep(2000); // Longer delay between batches

                        } catch (Exception e) {
                            logger.error("Error adding batch to vector store: {}", e.getMessage(), e);
                            // Continue with next batch even if one fails
                        }
                    }

                } catch (Exception e) {
                    logger.error("FAILED: Could not add even a single document: {}", e.getMessage(), e);
                    logger.error("This suggests the embedding service or vector store has a fundamental issue");
                    return; // Don't continue if we can't even add one document
                }

                // Verify the addition worked
                try {
                    logger.info("Verifying vector store population...");
                    List<Document> verifyDocs = this.vectorStore.similaritySearch("album");
                    logger.info("Verification: Vector store now contains {} documents", verifyDocs.size());
                } catch (Exception e) {
                    logger.warn("Could not verify vector store contents: {}", e.getMessage());
                }
            } else {
                logger.warn("No documents to add to vector store");
            }

            logger.info("=== Vector Store Initialization Complete ===");

        } catch (Exception e) {
            logger.error("Error populating vector store", e);
        }
    }
}