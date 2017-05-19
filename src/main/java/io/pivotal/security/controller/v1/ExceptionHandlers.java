package io.pivotal.security.controller.v1;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.jayway.jsonpath.InvalidJsonException;
import io.pivotal.security.exceptions.AuditSaveFailureException;
import io.pivotal.security.exceptions.EntryNotFoundException;
import io.pivotal.security.exceptions.InvalidQueryParameterException;
import io.pivotal.security.exceptions.KeyNotFoundException;
import io.pivotal.security.exceptions.ParameterizedValidationException;
import io.pivotal.security.exceptions.PermissionException;
import io.pivotal.security.view.ResponseError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.InvalidObjectException;

@RestControllerAdvice
public class ExceptionHandlers {
  private final MessageSourceAccessor messageSourceAccessor;

  @Autowired
  ExceptionHandlers(MessageSource messageSource) {
    messageSourceAccessor = new MessageSourceAccessor(messageSource);
  }
//
//  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
//  @ExceptionHandler(Exception.class)
//  @ResponseBody
//  public Map<String, Object> handleGeneralException(HttpServletRequest request) {
//    RequestAttributes requestAttributes = new ServletRequestAttributes(request);
//    return new DefaultErrorAttributes()
//        .getErrorAttributes(requestAttributes, false);
//  }

  @ExceptionHandler(EntryNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ResponseError handleNotFoundException(EntryNotFoundException e) {
    return constructError(e.getMessage());
  }

  @ExceptionHandler(PermissionException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ResponseError handlePermissionException(PermissionException error) {
    return constructError(error.getMessage());
  }

  @ExceptionHandler(JsonMappingException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ResponseError handleJsonMappingException(JsonMappingException e) {
    for (com.fasterxml.jackson.databind.JsonMappingException.Reference reference : e.getPath()) {
      if ("operations".equals(reference.getFieldName())) {
        return constructError("error.acl.invalid_operation");
      }
    }

    return badRequestResponse();
  }

  @ExceptionHandler(InvalidQueryParameterException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ResponseError handleInvalidParameterException(InvalidQueryParameterException e) {
    return constructError(e.getMessage(), e.getMissingQueryParameter());
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ResponseError handleMissingParameterException(MissingServletRequestParameterException e) {
    return constructError("error.missing_query_parameter", e.getParameterName());
  }

  @ExceptionHandler(JsonParseException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ResponseError handleJsonMappingException(JsonParseException e) {
    return badRequestResponse();
  }

  @ExceptionHandler(ParameterizedValidationException.class)
  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  public ResponseError handleParameterizedValidationException(
      ParameterizedValidationException exception
  ) {
    return constructError(exception.getMessage(), exception.getParameters());
  }

  @ExceptionHandler(UnrecognizedPropertyException.class)
  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  public ResponseError handleUnrecognizedPropertyException(UnrecognizedPropertyException exception) {
    return constructError("error.invalid_json_key", exception.getPropertyName());
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, InvalidJsonException.class, InvalidFormatException.class})
  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  public ResponseError handleInputNotReadableException(Exception exception) {
    final Throwable cause = exception.getCause() == null ? exception : exception.getCause();
    if (cause instanceof UnrecognizedPropertyException) {
      return constructError("error.invalid_json_key", ((UnrecognizedPropertyException) cause).getPropertyName());
    } else if (cause instanceof InvalidTypeIdException
        || (cause instanceof JsonMappingException && cause.getMessage()
        .contains("missing property 'type'"))) {
      return constructError("error.invalid_type_with_set_prompt");
    } else if (cause instanceof InvalidFormatException) {
      for (InvalidFormatException.Reference reference : ((InvalidFormatException) cause)
          .getPath()) {
        if ("operations".equals(reference.getFieldName())) {
          return constructError("error.acl.invalid_operation");
        }
      }
    }
    return badRequestResponse();
  }

  @ExceptionHandler(AuditSaveFailureException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ResponseError handleAuditSaveFailureException(AuditSaveFailureException e) {
    return constructError(e.getMessage());
  }

  @ExceptionHandler(KeyNotFoundException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ResponseError handleKeyNotFoundException(KeyNotFoundException e) {
    return constructError(e.getMessage());
  }

  @ExceptionHandler(InvalidObjectException.class)
  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  public ResponseError handleInvalidTypeAccess(InvalidObjectException exception) {
    return constructError(exception.getMessage());
  }

  private ResponseError badRequestResponse() {
    return constructError("error.bad_request");
  }

  private ResponseError constructError(String error) {
    return new ResponseError(messageSourceAccessor.getMessage(error));
  }

  private ResponseError constructError(String error, String... args) {
    return new ResponseError(messageSourceAccessor.getMessage(error, args));
  }

  private ResponseError constructError(String error, Object[] args) {
    return new ResponseError(messageSourceAccessor.getMessage(error, args));
  }
}
