package com.neaz.travel.visa.domain.model;

/**
 * Lifecycle state of an {@link Application}.
 */
public enum ApplicationStatus {

    /** Created and being worked on (checklist/consistency review); the default state. */
    OPEN,

    /** The applicant abandoned this application before any submission-related work. Terminal. */
    WITHDRAWN
}
