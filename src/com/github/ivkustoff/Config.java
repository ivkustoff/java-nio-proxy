package com.github.ivkustoff;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Config {
    private final Path configProperties;
    private final List<ConfigEntity> configEntityList = new ArrayList<>();

    public int size() {
        return configEntityList.size();
    }

    public List<ConfigEntity> configEntities() {
        return configEntityList;
    }

    public Config(Path configProperties) {
        this.configProperties = configProperties;
    }

    public boolean isCorrectConfig() {
        return configEntityList.size() > 0;
    }

    public List<String> parseConfig() {
        List<String> messages = new ArrayList<>();
        List<String> configLines = Collections.emptyList();
        try {
            configLines = Files.readAllLines(configProperties);
        } catch (IOException ex) {
            System.out.println("Exception while parsing: " + ex);
        }
        int lineNumber = 0;
        for (String line : configLines) {
            lineNumber++;
            if (!line.startsWith("#")) {
                ValidationObject validationObject = new ValidationObject(line, lineNumber);
                validationObject.lineContainsEqualSign().lineContainsSplitter().normalLocalPort().normalRemoteHost().normalRemotePort();
                messages.addAll(validationObject.getMessages());
                if (validationObject.isValidLine()) {
                    configEntityList.add(new ConfigEntity(validationObject));
                }
            }
        }
        return messages;
    }

    static class ConfigEntity {
        private final int localPort;
        private final int remotePort;
        private final String remoteHost;

        public ConfigEntity(ValidationObject validationObject) {
            this.localPort = validationObject.localPort;
            this.remotePort = validationObject.remotePort;
            this.remoteHost = validationObject.remoteHost;
        }

        public int getLocalPort() {
            return localPort;
        }

        public int getRemotePort() {
            return remotePort;
        }

        public String getRemoteHost() {
            return remoteHost;
        }

        @Override
        public String toString() {
            return localPort + "->" + remoteHost + ":" + remotePort;
        }
    }

    static class ValidationObject {
        private List<String> validationMessages = new ArrayList<>();
        private boolean skipFurtherValidation = false;
        private String initialLine;
        private List<String> splittedByEqualsSign = new ArrayList<>();
        private List<String> splittedByArrow = new ArrayList<>();
        private List<String> splittedByColon = new ArrayList<>();
        String messageTemplate = "";

        private int localPort = -1;
        private int remotePort = -1;
        private String remoteHost = "";

        public ValidationObject(final String initialLine, final int lineNumber) {
            this.initialLine = initialLine;
            this.messageTemplate = lineNumber + ": " + initialLine;
        }

        public ValidationObject lineContainsEqualSign() {
            if (initialLine.isEmpty()) {
                skipFurtherValidation = true;
                return this;
            } else {
                if (split(initialLine, "=").size() < 2) {
                    validationMessages.add("Line not in format alias=localPort->remoteHost:remotePort\n" + messageTemplate);
                    skipFurtherValidation = true;
                    return this;
                } else {
                    splittedByEqualsSign = split(initialLine, "=");
                    return this;

                }
            }
        }

        public ValidationObject lineContainsSplitter() {
            if (skipFurtherValidation) {
                return this;
            } else {
                if (splittedByEqualsSign.size() < 2) {
                    validationMessages.add("ill formed line " + messageTemplate);
                    skipFurtherValidation = true;
                    return this;
                }
                if (!splittedByEqualsSign.get(1).contains("->")) {
                    validationMessages.add("Line doesn't contain -> splitter:\n" + messageTemplate);
                    skipFurtherValidation = true;
                    return this;
                } else {
                    splittedByArrow = split(splittedByEqualsSign.get(1), "->");
                    return this;
                }
            }
        }

        public ValidationObject normalLocalPort() {
            if (skipFurtherValidation) {
                return this;
            } else {
                if (splittedByArrow.size() < 2) {
                    validationMessages.add("ill formed line " + messageTemplate);
                    skipFurtherValidation = true;
                    return this;
                }

                this.localPort = parsePort(splittedByArrow.get(0));
                return this;
            }
        }

        public ValidationObject normalRemoteHost() {
            if (skipFurtherValidation) {
                return this;
            } else {
                String remoteHostPort = splittedByArrow.get(1).trim();
                if (!remoteHostPort.contains(":")) {
                    skipFurtherValidation = true;
                    validationMessages.add("remote host should be in form remoteHost:remotePort for line:\n" + messageTemplate);
                    return this;
                } else {
                    splittedByColon = split(remoteHostPort, ":");
                    remoteHost = splittedByColon.get(0);
                    return this;
                }
            }
        }

        public ValidationObject normalRemotePort() {
            if (skipFurtherValidation) {
                return this;
            } else {
                remotePort = parsePort(splittedByColon.get(1));
                return this;
            }
        }

        private List<String> split(final String line, final String separator) {
            return Arrays.asList(line.split(separator));
        }

        private boolean inSegment(final int intValue, final int min, final int max) {
            return (intValue <= max) && (intValue >= min);
        }

        private Integer parsePort(final String line) {
            Integer port = -1;
            try {
                port = Integer.parseInt(line.trim());
            } catch (NumberFormatException ex) {
                validationMessages.add("Couldn't parse " + line + " as port for line:\n" + messageTemplate);
                this.skipFurtherValidation = true;
                return port;
            }
            if (!inSegment(port, 1, 65535)) {
                port = -1;
            }
            return port;
        }

        public List<String> getMessages() {
            return validationMessages;
        }

        public boolean isValidLine() {
            return
                    this.localPort != -1 && this.remotePort != -1 && !this.remoteHost.isEmpty();
        }
    }
}
