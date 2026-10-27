package cat.albirar.dynmod.filtering.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Invalid model selection in the request. Results in HTTP 400 Bad Request.
 */
@SuppressWarnings("serial")
public class ModelFilteringException extends ResponseStatusException {

    public ModelFilteringException(String reason) {
        super(HttpStatus.BAD_REQUEST, reason);
    }

    public ModelFilteringException(String reason, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, reason, cause);
    }
}
