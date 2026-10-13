-- ==============================================================================
-- GeoAttend PostgreSQL Schema (Neon / Production)
-- Fully idempotent with IF NOT EXISTS checks
-- ==============================================================================

-- 1. Batches / Cohorts
CREATE TABLE IF NOT EXISTS batches (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_batches_status ON batches (status);

-- 2. Club Users (Administrative staff)
CREATE TABLE IF NOT EXISTS club_users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(320) NOT NULL UNIQUE,
    role VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL
);

-- 3. Members
CREATE TABLE IF NOT EXISTS members (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(320) NOT NULL UNIQUE,
    role VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
    password_hash VARCHAR(255) NOT NULL,
    batch_id BIGINT NULL REFERENCES batches (id) ON DELETE SET NULL,
    student_id VARCHAR(50) NULL
);
CREATE INDEX IF NOT EXISTS idx_members_batch_id ON members (batch_id);

-- 4. Member Registration Requests
CREATE TABLE IF NOT EXISTS member_signups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    requested_batch_id BIGINT NULL REFERENCES batches (id) ON DELETE SET NULL,
    student_id VARCHAR(50) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(500) NULL,
    reviewed_by BIGINT NULL,
    reviewed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_member_signups_status ON member_signups (status);

-- 5. Attendance Sessions
CREATE TABLE IF NOT EXISTS attendance_sessions (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    radius_meters INT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sessions_time_window ON attendance_sessions (start_time, end_time);

-- 6. Official Finalized Attendance
CREATE TABLE IF NOT EXISTS attendance (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES attendance_sessions (id) ON DELETE CASCADE,
    member_id BIGINT NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    marked_at TIMESTAMP NOT NULL,
    latitude DECIMAL(10,7) NULL,
    longitude DECIMAL(10,7) NULL,
    attendance_method VARCHAR(50) NOT NULL,
    CONSTRAINT uq_attendance_session_member UNIQUE (session_id, member_id)
);
CREATE INDEX IF NOT EXISTS idx_attendance_session ON attendance (session_id);
CREATE INDEX IF NOT EXISTS idx_attendance_member ON attendance (member_id);

-- 7. Real-Time Attendance Tracking (10-slot engine)
CREATE TABLE IF NOT EXISTS attendance_tracking (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES attendance_sessions (id) ON DELETE CASCADE,
    member_id BIGINT NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    started_at TIMESTAMP NOT NULL,
    last_check_at TIMESTAMP NOT NULL,
    total_checks INT NOT NULL DEFAULT 0,
    inside_checks INT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'TRACKING',
    finalized_at TIMESTAMP NULL,
    CONSTRAINT uq_attendance_tracking_session_member UNIQUE (session_id, member_id)
);
CREATE INDEX IF NOT EXISTS idx_attendance_tracking_status ON attendance_tracking (status);
CREATE INDEX IF NOT EXISTS idx_attendance_tracking_member ON attendance_tracking (member_id);

-- 8. Individual Interval Location Checks
CREATE TABLE IF NOT EXISTS attendance_location_checks (
    id BIGSERIAL PRIMARY KEY,
    tracking_id BIGINT NOT NULL REFERENCES attendance_tracking (id) ON DELETE CASCADE,
    check_number INT NOT NULL,
    checked_at TIMESTAMP NOT NULL,
    latitude DECIMAL(10,7) NULL,
    longitude DECIMAL(10,7) NULL,
    inside_geofence BOOLEAN NOT NULL,
    CONSTRAINT uq_location_check_number UNIQUE (tracking_id, check_number)
);
CREATE INDEX IF NOT EXISTS idx_location_check_tracking_time ON attendance_location_checks (tracking_id, checked_at);

-- 9. Fine-Grained Audit Log
CREATE TABLE IF NOT EXISTS attendance_location_audit (
    tracking_id BIGINT NOT NULL REFERENCES attendance_tracking (id) ON DELETE CASCADE,
    check_number INT NOT NULL,
    checked_at TIMESTAMP NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    accuracy_meters DECIMAL(10,2) NULL,
    distance_meters DECIMAL(10,2) NOT NULL,
    inside_geofence BOOLEAN NOT NULL,
    accuracy_status VARCHAR(30) NULL DEFAULT 'HIGH',
    boundary_tolerance_meters DECIMAL(10,2) NULL,
    evaluation_reason VARCHAR(255) NULL,
    PRIMARY KEY (tracking_id, check_number)
);
CREATE INDEX IF NOT EXISTS idx_location_audit_time ON attendance_location_audit (checked_at);
