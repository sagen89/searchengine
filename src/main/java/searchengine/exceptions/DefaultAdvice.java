package searchengine.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import searchengine.dto.indexing.ErrorResponse;

@ControllerAdvice
public class DefaultAdvice {

    @ExceptionHandler(StartIsNotPossible.class)
    public ResponseEntity<ErrorResponse> handleException(StartIsNotPossible ex) {
        ErrorResponse response = new ErrorResponse(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(StopIsNotPossible.class)
    public ResponseEntity<ErrorResponse> handleException(StopIsNotPossible ex) {
        ErrorResponse response = new ErrorResponse(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(IndexPageIsNotPossible.class)
    public ResponseEntity<ErrorResponse> handleException(IndexPageIsNotPossible ex) {
        ErrorResponse response = new ErrorResponse(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

}
