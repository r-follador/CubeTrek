package com.cubetrek;

import com.cubetrek.viewer.TrackViewerService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.apache.tomcat.util.http.fileupload.impl.SizeLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice
public class ExceptionHandling {

    Logger logger = LoggerFactory.getLogger(ExceptionHandling.class);

    @ExceptionHandler({FileNotAccepted.class})
    public final ResponseEntity<Object> handleWrongFileException(FileNotAccepted ex) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setResponse(ex.msg);

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, SizeLimitExceededException.class, FileSizeLimitExceededException.class})
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

    @ExceptionHandler({UnnamedExceptionJson.class})
    public final ResponseEntity<Object> handleTrackViewer(UnnamedExceptionJson ex) {

        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setResponse(ex.msg);

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({TrackAccessException.class})
    public final String handleTrackViewerAccess(TrackAccessException ex) {
        logger.info("- Track Access Exception");
        return "trackAccessError";
    }

    @ExceptionHandler({UnnamedException.class})
    public final String handlUnnamedException(UnnamedException ex, Model model) {
        model.addAttribute("errormessage", ex.title);
        model.addAttribute("errormessagedetail", ex.msg);
        return "custom_error";
    }

    @ExceptionHandler({EditTrackmetadataException.class})
    public final ResponseEntity<Object> handleEditTrackmetadaata(EditTrackmetadataException ex) {

        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setResponse(ex.msg);

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }



    @AllArgsConstructor
    public static class FileNotAccepted extends RuntimeException {
        public String msg ="";
    }

    @AllArgsConstructor
    public static class UnnamedException extends RuntimeException {
        public String title = "";
        public String msg ="";
    }

    @AllArgsConstructor
    public static class UnnamedExceptionJson extends RuntimeException {
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
    public static class TrackAccessException extends RuntimeException {
        public String msg ="";
    }

    @AllArgsConstructor
    public static class EditTrackmetadataException extends RuntimeException {
        public String msg ="";
    }

    public static class ErrorResponse{
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
