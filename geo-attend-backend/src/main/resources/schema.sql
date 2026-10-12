CREATE TABLE IF NOT EXISTS batches (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_batches_name (name),
    KEY idx_batches_status (status)
);

CREATE TABLE IF NOT EXISTS member_signups (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    requested_batch_id BIGINT NULL,
    student_id VARCHAR(50) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(500) NULL,
    reviewed_by BIGINT NULL,
    reviewed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_member_signups_email (email),
    KEY idx_member_signups_status (status)
);

CREATE TABLE IF NOT EXISTS attendance_tracking (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    started_at DATETIME NOT NULL,
    last_check_at DATETIME NOT NULL,
    total_checks INT NOT NULL DEFAULT 0,
    inside_checks INT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'TRACKING',
    finalized_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_attendance_tracking_session_member (session_id, member_id),
    KEY idx_attendance_tracking_status (status),
    KEY idx_attendance_tracking_member (member_id)
);

CREATE TABLE IF NOT EXISTS attendance_location_checks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tracking_id BIGINT NOT NULL,
    check_number INT NOT NULL,
    checked_at DATETIME NOT NULL,
    latitude DECIMAL(10,7) NULL,
    longitude DECIMAL(10,7) NULL,
    inside_geofence BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_location_check_number (tracking_id, check_number),
    KEY idx_location_check_tracking_time (tracking_id, checked_at),
    CONSTRAINT fk_location_check_tracking FOREIGN KEY (tracking_id)
        REFERENCES attendance_tracking (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS attendance_location_audit (
    tracking_id BIGINT NOT NULL,
    check_number INT NOT NULL,
    checked_at DATETIME NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    accuracy_meters DECIMAL(10,2) NULL,
    distance_meters DECIMAL(10,2) NOT NULL,
    inside_geofence BOOLEAN NOT NULL,
    PRIMARY KEY (tracking_id, check_number),
    KEY idx_location_audit_time (checked_at),
    CONSTRAINT fk_location_audit_tracking FOREIGN KEY (tracking_id)
        REFERENCES attendance_tracking (id) ON DELETE CASCADE
);

ALTER TABLE attendance_location_checks
    MODIFY COLUMN latitude DECIMAL(10,7) NULL;

ALTER TABLE attendance_location_checks
    MODIFY COLUMN longitude DECIMAL(10,7) NULL;