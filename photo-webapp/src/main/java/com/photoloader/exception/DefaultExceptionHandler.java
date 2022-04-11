package com.photoloader.exception;


import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

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

    @ExceptionHandler(value = NotFoundException.class)
    @ResponseBody
    public GenericError notFoundError(NotFoundException ex, HttpServletResponse response) {
        log.info("Suppressing the error:" +  ex.getMessage());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return new GenericError(ex.getMessage(), ExceptionUtils.getStackTrace(ex));
    }


}
