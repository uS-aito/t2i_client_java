package com.github.us_aito.t2iclient.cli;

import java.util.ArrayList;
import java.util.List;

public record AppArgs(
        AppMode mode,
        String configPath,
        String sqsQueueUrl,
        String s3DestinationUri
) {
    public enum AppMode { FILE, CLIENT, SERVER }

    public static final class InvalidArgumentException extends RuntimeException {
        public InvalidArgumentException(String message) {
            super(message);
        }
    }

    public static AppArgs parse(String[] rawArgs) {
        boolean clientFlag = false;
        boolean serverFlag = false;
        String sqsQueueUrl = null;
        String s3DestinationUri = null;
        List<String> positional = new ArrayList<>();

        for (int i = 0; i < rawArgs.length; i++) {
            String arg = rawArgs[i];
            switch (arg) {
                case "--client" -> clientFlag = true;
                case "--server" -> serverFlag = true;
                case "--sqs" -> {
                    if (i + 1 >= rawArgs.length) {
                        throw new InvalidArgumentException("--sqs requires a value");
                    }
                    sqsQueueUrl = rawArgs[++i];
                }
                case "--s3" -> {
                    if (i + 1 >= rawArgs.length) {
                        throw new InvalidArgumentException("--s3 requires a value");
                    }
                    s3DestinationUri = rawArgs[++i];
                }
                default -> {
                    if (arg.startsWith("--")) {
                        throw new InvalidArgumentException("unknown option: " + arg);
                    }
                    positional.add(arg);
                }
            }
        }

        if (clientFlag && serverFlag) {
            throw new InvalidArgumentException("--client and --server are mutually exclusive");
        }

        if (s3DestinationUri != null && !serverFlag) {
            throw new InvalidArgumentException("--s3 is only valid with --server");
        }

        if (serverFlag) {
            if (sqsQueueUrl == null) {
                throw new InvalidArgumentException("--sqs is required when --server is specified");
            }
            if (s3DestinationUri == null) {
                throw new InvalidArgumentException("--s3 is required when --server is specified");
            }
            if (!positional.isEmpty()) {
                throw new InvalidArgumentException("config path must not be specified with --server");
            }
            return new AppArgs(AppMode.SERVER, null, sqsQueueUrl, s3DestinationUri);
        }

        if (positional.isEmpty()) {
            throw new InvalidArgumentException("config path is required");
        }
        String configPath = positional.get(0);

        if (clientFlag) {
            if (sqsQueueUrl == null) {
                throw new InvalidArgumentException("--sqs is required when --client is specified");
            }
            return new AppArgs(AppMode.CLIENT, configPath, sqsQueueUrl, null);
        }

        return new AppArgs(AppMode.FILE, configPath, null, null);
    }
}
