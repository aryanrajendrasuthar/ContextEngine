
package com.contextengine.connector.exception;

public class ConnectorException extends Exception {

    private final String connectorId;

    public ConnectorException(String connectorId, String message) {
        super(message);
        this.connectorId = connectorId;
    }

    public ConnectorException(String connectorId, String message, Throwable cause) {
        super(message, cause);
        this.connectorId = connectorId;
    }

    public String getConnectorId() {
        return connectorId;
    }
}
