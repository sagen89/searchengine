package searchengine.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import searchengine.dto.indexing.ErrorResponse;

@ControllerAdvice
public class DefaultAdvice {

    @ExceptionHandler(StartIndexingIsNotPossible.class)
    public ResponseEntity<ErrorResponse> handleException(StartIndexingIsNotPossible ex) {
        ErrorResponse response = new ErrorResponse(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(StopIndexingIsNotPossible.class)
    public ResponseEntity<ErrorResponse> handleException(StopIndexingIsNotPossible ex) {
        ErrorResponse response = new ErrorResponse(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(IndexPageIsNotPossible.class)
    public ResponseEntity<ErrorResponse> handleException(IndexPageIsNotPossible ex) {
        ErrorResponse response = new ErrorResponse(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(QueryIsEmpty.class)
    public ResponseEntity<ErrorResponse> handleException(QueryIsEmpty ex) {
        ErrorResponse response = new ErrorResponse(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(QueryFormatIsWrong.class)
    public ResponseEntity<ErrorResponse> handleException(QueryFormatIsWrong ex) {
        ErrorResponse response = new ErrorResponse(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

}
