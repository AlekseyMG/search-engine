package searchengine.api.response;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class ErrorResponse extends DefaultResponse {

    private final boolean result = false;
    private final String error;

    public ErrorResponse(String text) {
        this.error = text;
    }
}
