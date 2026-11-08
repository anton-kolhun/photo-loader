package com.photoloader.exception;


import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
@Slf4j
public class DefaultExceptionHandler {

    @ExceptionHandler(value = Exception.class)
    @ResponseBody
    public GenericError unhandledError(Exception ex, HttpServletResponse response) {
        log.error("Error occurred:", ex);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        return new GenericError(ex.getMessage(), ExceptionUtils.getStackTrace(ex));
    }

    /** Unknown URL: a plain 404, not a 500 with a logged stack trace. */
    @ExceptionHandler(value = NoResourceFoundException.class)
    @ResponseBody
    public GenericError noSuchPage(NoResourceFoundException ex, HttpServletResponse response) {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        return new GenericError(ex.getMessage(), null);
    }

    @ExceptionHandler(value = NotFoundException.class)
    @ResponseBody
    public GenericError notFoundError(NotFoundException ex, HttpServletResponse response) {
        log.info("Suppressing the error:" +  ex.getMessage());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return new GenericError(ex.getMessage(), ExceptionUtils.getStackTrace(ex));
    }


}
