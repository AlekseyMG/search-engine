package searchengine.api.response;

import lombok.Getter;

//@Data
@Getter
public class ErrorResponse extends DefaultResponse {

    private final boolean result = false;
    private final String error;

    public ErrorResponse(String text) {
        this.error = text;
    }
}