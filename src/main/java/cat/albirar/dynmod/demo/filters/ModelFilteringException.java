package cat.albirar.dynmod.demo.filters;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when model filtering fails due to invalid filter codes.
 * Results in HTTP 400 Bad Request response.
 */
@SuppressWarnings("serial")
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ModelFilteringException extends RuntimeException {
    
    /**
     * Creates a new ModelFilteringException with the specified message.
     * 
     * @param message The detail message describing the error
     */
    public ModelFilteringException(String message) {
        super(message);
    }
}
