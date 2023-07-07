package searchengine.exceptions;

public class QueryIsEmpty extends RuntimeException{
    public QueryIsEmpty(String message) {
        super(message);
    }
}
