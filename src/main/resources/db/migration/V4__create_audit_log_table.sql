CREATE TABLE audit_log (
                           id              BIGINT          NOT NULL AUTO_INCREMENT,
                           event_type      VARCHAR(50)     NOT NULL,
                           user_id         BIGINT          NOT NULL,
                           account_number      VARCHAR(20) NOT NULL,
                           counterparty_account_number VARCHAR(20),
                           amount          DECIMAL(14,2),
                           result          VARCHAR(20)     NOT NULL,    -- SUCCESS, DECLINED, FAILED
                           request_id      VARCHAR(40),
                           trace_id        VARCHAR(40),
                           ip_address      VARCHAR(45),                  -- IPv6 max length
                           user_agent      VARCHAR(255),
                           detail          VARCHAR(1000),
                           occurred_at     DATETIME(6)     NOT NULL,
                           PRIMARY KEY (id),
                           INDEX idx_audit_user        (user_id, occurred_at),
                           INDEX idx_audit_event_type  (event_type, occurred_at),
                           INDEX idx_audit_trace       (trace_id),
                           INDEX idx_audit_account     (account_number, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;