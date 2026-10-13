CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NULL,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE projects (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_code VARCHAR(32) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    department VARCHAR(120) NOT NULL,
    location VARCHAR(200) NOT NULL,
    approved_budget DECIMAL(16,2) NOT NULL,
    progress_percent INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    public_visible BOOLEAN NOT NULL DEFAULT TRUE,
    start_date DATE NOT NULL,
    expected_completion_date DATE NOT NULL,
    actual_completion_date DATE NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_projects_code UNIQUE (project_code),
    CONSTRAINT chk_progress CHECK (progress_percent >= 0 AND progress_percent <= 100)
);

CREATE INDEX idx_projects_department ON projects (department);
CREATE INDEX idx_projects_status ON projects (status);
CREATE INDEX idx_projects_location ON projects (location);

CREATE TABLE project_assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by BIGINT NULL,
    CONSTRAINT fk_assign_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_assign_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_assign_by FOREIGN KEY (assigned_by) REFERENCES users (id),
    CONSTRAINT uk_project_user UNIQUE (project_id, user_id)
);

CREATE TABLE progress_updates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    officer_id BIGINT NOT NULL,
    progress_percent INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    description TEXT NOT NULL,
    milestone VARCHAR(200) NULL,
    update_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_update_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_update_officer FOREIGN KEY (officer_id) REFERENCES users (id)
);

CREATE INDEX idx_updates_project ON progress_updates (project_id);

CREATE TABLE citizen_reports (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    public_reference VARCHAR(32) NOT NULL,
    tracking_token CHAR(36) NOT NULL,
    project_id BIGINT NOT NULL,
    reporter_id BIGINT NULL,
    category VARCHAR(40) NOT NULL,
    subject VARCHAR(180) NOT NULL,
    description TEXT NOT NULL,
    issue_location VARCHAR(200) NULL,
    status VARCHAR(30) NOT NULL,
    anonymous BOOLEAN NOT NULL DEFAULT FALSE,
    consent_acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_report_reference UNIQUE (public_reference),
    CONSTRAINT uk_report_token UNIQUE (tracking_token),
    CONSTRAINT fk_report_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_report_user FOREIGN KEY (reporter_id) REFERENCES users (id)
);

CREATE INDEX idx_reports_status ON citizen_reports (status);
CREATE INDEX idx_reports_project ON citizen_reports (project_id);

CREATE TABLE evidence_files (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    public_visible BOOLEAN NOT NULL DEFAULT FALSE,
    uploaded_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evidence_report FOREIGN KEY (report_id) REFERENCES citizen_reports (id),
    CONSTRAINT fk_evidence_user FOREIGN KEY (uploaded_by) REFERENCES users (id)
);

CREATE TABLE report_responses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_response_report FOREIGN KEY (report_id) REFERENCES citizen_reports (id),
    CONSTRAINT fk_response_author FOREIGN KEY (author_id) REFERENCES users (id)
);

CREATE TABLE notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    message TEXT NOT NULL,
    read_flag BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_id BIGINT NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id VARCHAR(80) NULL,
    details TEXT NULL,
    ip_address VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES users (id)
);

CREATE INDEX idx_audit_created ON audit_logs (created_at);

CREATE TABLE system_settings (
    setting_key VARCHAR(80) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO system_settings (setting_key, setting_value) VALUES
    ('anonymous_reports_enabled', 'true'),
    ('demo_mode', 'true');
