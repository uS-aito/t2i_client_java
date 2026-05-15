package com.github.us_aito.t2iclient.client_mode;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.us_aito.t2iclient.config_loader.Config;
import com.github.us_aito.t2iclient.config_loader.ConfigLoader;
import com.github.us_aito.t2iclient.config_loader.DefaultPrompts;
import com.github.us_aito.t2iclient.config_loader.Scene;
import com.github.us_aito.t2iclient.library_loader.LibraryLoader;
import com.github.us_aito.t2iclient.prompt_generator.PromptGenerator;
import com.github.us_aito.t2iclient.prompt_generator.WorkflowPatcher;
import com.github.us_aito.t2iclient.workflow_loader.WorkflowLoader;
import com.github.us_aito.t2iclient.workflow_manager.WorkflowManager;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
public final class ClientRunner {

    public enum ExitCode {
        SUCCESS(0),
        CONFIG_ERROR(1),
        ARGUMENT_ERROR(2),
        AUTH_ERROR(3),
        PUBLISH_ERROR(4),
        UNEXPECTED(9);

        private final int code;
        ExitCode(int code) { this.code = code; }
        public int numeric() { return code; }
    }

    private final SqsPromptPublisher publisherOverride;

    public ClientRunner() {
        this.publisherOverride = null;
    }

    ClientRunner(SqsPromptPublisher publisher) {
        this.publisherOverride = publisher;
    }

    public ExitCode run(String configPath, String sqsQueueUrl) {
        log.info("Starting client mode: configPath={}, queueUrl={}", configPath, sqsQueueUrl);
        Instant start = Instant.now();

        SqsPromptPublisher publisher = publisherOverride != null
            ? publisherOverride
            : new SqsPromptPublisher(sqsQueueUrl);

        try {
            Config config = ConfigLoader.loadConfig(configPath);
            ObjectNode workflow = WorkflowLoader.loadWorkflow(config.workflowConfig().workflowJsonPath());
            Map<String, List<String>> library = LibraryLoader.loadLibrary(config.workflowConfig().libraryFilePath());
            WorkflowManager workflowManager = new WorkflowManager();
            ObjectMapper objectMapper = new ObjectMapper();
            Random random = new Random();
            DefaultPrompts defaults = config.workflowConfig().defaultPrompts();

            publisher.healthCheck();

            int totalPublished = 0;
            for (Scene scene : config.scenes()) {
                String basePositive = scene.basePositivePrompt() != null
                    ? scene.basePositivePrompt()
                    : (defaults != null ? defaults.basePositivePrompt() : "");
                String positive = scene.positivePrompt() != null
                    ? scene.positivePrompt()
                    : (defaults != null ? defaults.positivePrompt() : "");
                String negative = scene.negativePrompt() != null
                    ? scene.negativePrompt()
                    : (defaults != null ? defaults.negativePrompt() : "");
                String environment = scene.environmentPrompt() != null
                    ? scene.environmentPrompt()
                    : (defaults != null ? defaults.environmentPrompt() : "");
                int batchSize = scene.batchSize() != null
                    ? scene.batchSize()
                    : (defaults != null && defaults.batchSize() != null ? defaults.batchSize() : 1);

                List<String> prompts = PromptGenerator.generatePrompts(positive, library, batchSize);
                int promptNumber = 0;
                for (String prompt : prompts) {
                    promptNumber++;
                    String fullPrompt = basePositive != null && !basePositive.isEmpty()
                        ? String.join(", ", basePositive, prompt)
                        : prompt;
                    long seed = random.nextLong(0, 1125899906842624L);
                    WorkflowPatcher.applyScenePrompts(
                        workflow,
                        config.workflowConfig(),
                        fullPrompt,
                        negative,
                        environment,
                        seed
                    );
                    Map<String, Object> workflowData = objectMapper.convertValue(
                        workflow, new TypeReference<>() {}
                    );
                    String body = workflowManager.buildPromptBody(
                        config.comfyuiConfig().clientId(), workflowData
                    );
                    String messageId = publisher.publish(body);
                    log.info("Published: scene={}, prompt={}/{}, messageId={}", scene.name(), promptNumber, batchSize, messageId);
                    totalPublished++;
                }
            }

            long elapsedMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            log.info("Client mode complete: published={} messages in {}ms", totalPublished, elapsedMs);
            return ExitCode.SUCCESS;

        } catch (IOException | IllegalArgumentException e) {
            log.error("Config error: {}", e.getMessage(), e);
            return ExitCode.CONFIG_ERROR;
        } catch (QueueDoesNotExistException | SdkClientException e) {
            log.error("Auth/connectivity error: {}", e.getMessage(), e);
            return ExitCode.AUTH_ERROR;
        } catch (SqsPromptPublisher.MessageTooLongException | SdkException e) {
            log.error("Publish error: {}", e.getMessage(), e);
            return ExitCode.PUBLISH_ERROR;
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            return ExitCode.UNEXPECTED;
        }
    }
}
