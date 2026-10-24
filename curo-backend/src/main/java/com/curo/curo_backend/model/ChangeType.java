package com.curo.curo_backend.model;

public enum ChangeType {
    ISSUED,
    MODIFIED,
    // the end date moved later; the version records the old and the new end date
    EXTENDED,
    DISCONTINUED
}
