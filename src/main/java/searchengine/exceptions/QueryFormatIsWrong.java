package searchengine.exceptions;

public class QueryFormatIsWrong extends RuntimeException{
    public QueryFormatIsWrong(String message) {
        super(message);
    }
}
