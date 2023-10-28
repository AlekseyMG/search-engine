package searchengine.services;

import lombok.NoArgsConstructor;
import searchengine.dto.ErrorMessages;
import searchengine.model.StatusType;

@NoArgsConstructor
public class ErrorHandler {
    public void processError(Exception ex, WebParser webParser) {
        if (ex.toString().contains("UnknownHostException") || ex.toString().contains("IOException")) {
            IOException(webParser);
        }
        if (ex.toString().contains("SocketTimeout")) {
            SocketTimeout(ex, webParser);
        }
        if (ex.toString().contains("Interrupted")) {
            Interrupted(webParser);
        }
        if (ex.toString().contains("DataIntegrityViolation")) {
            DataIntegrityViolation(ex, webParser);
        }
    }

    private void SocketTimeout(Exception ex, WebParser webParser) {
        String errorMessage;
        if (webParser.getResponse() != null) {
            webParser.setStatusCode(webParser.getResponse().statusCode());
        }
        if (ex.toString().contains("Connect timed out")) {
            webParser.setStatusCode(522);
        }
        if (ex.toString().contains("Read timed out")) {
            webParser.setStatusCode(598);
        }

        errorMessage =  ErrorMessages.CONNECTION_TIMED_OUT + webParser.getAbsolutePath();
        webParser.setErrorMessage(errorMessage);

        if (webParser.getAbsolutePath().equals(webParser.getSiteUrl())) {
            webParser.setCurrentSiteEntityStatus(StatusType.FAILED);
        }
        System.out.println(errorMessage);
    }
    private void Interrupted(WebParser webParser) {
        String errorMessage = ErrorMessages.ABORTED_BY_USER;
        webParser.setErrorMessage(errorMessage);
        System.out.println(errorMessage);
    }

    private void IOException(WebParser webParser) {
        String errorMessage = ErrorMessages.IO_OR_NOT_FOUND + webParser.getAbsolutePath();
        webParser.setErrorMessage(errorMessage);
        if (webParser.getAbsolutePath().equals(webParser.getSiteUrl())) {
            webParser.setCurrentSiteEntityStatus(StatusType.FAILED);
        }
        System.out.println(errorMessage);

    }

    private void DataIntegrityViolation(Exception ex, WebParser webParser) {
        String errorMessage = ErrorMessages.ERROR_ADD_ENTITY_TO_DB +
                (ex.toString().contains("Duplicate") ? " (дубликат)" : "");
        webParser.setErrorMessage(errorMessage);
        System.out.println(errorMessage);
    }
}
