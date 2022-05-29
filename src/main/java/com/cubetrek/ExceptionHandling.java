package com.cubetrek;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ExceptionHandling {

    @ExceptionHandler({FileNotAccepted.class})
    public final ResponseEntity<Object> handleWrongFileException(FileNotAccepted ex) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setResponse(ex.msg);

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({FileSizeLimitExceededException.class})
    public final ResponseEntity<Object> handleTooLargeFileException(FileSizeLimitExceededException ex) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setResponse("File is too large");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({UserRegistrationException.class})
    public final ResponseEntity<Object> handleUserAlreadyExists(UserRegistrationException ex) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setResponse(ex.msg);

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({TrackViewerException.class})
    public final ResponseEntity<Object> handleTrackViewer(TrackViewerException ex) {

        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setResponse(ex.msg);

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }


    @AllArgsConstructor
    public static class FileNotAccepted extends RuntimeException {
        public String msg ="";
    }

    @AllArgsConstructor
    public static class UserRegistrationException extends RuntimeException {
        public String msg ="";
    }

    @AllArgsConstructor
    public static class TrackViewerException extends RuntimeException {
        public String msg ="";
    }

    @AllArgsConstructor
    public static class EditTrackmetadataException extends RuntimeException {
        public String msg ="";
    }

    public class ErrorResponse{
        @Getter
        @Setter
        boolean error=true;

        @Getter
        @Setter
        String type;

        @Getter
        @Setter
        String response;
    }

}
