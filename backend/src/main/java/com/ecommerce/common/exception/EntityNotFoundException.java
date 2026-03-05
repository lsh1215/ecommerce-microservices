package com.ecommerce.common.exception;

/**
 * Exception thrown when a requested entity is not found.
 * Maps to HTTP 404 Not Found.
 */
public class EntityNotFoundException extends BusinessException {

    public EntityNotFoundException(String entityName, Object identifier) {
        super(ErrorCode.ENTITY_NOT_FOUND, entityName + " not found with id: " + identifier);
    }

    public EntityNotFoundException(String entityName) {
        super(ErrorCode.ENTITY_NOT_FOUND, entityName + " not found");
    }
}
