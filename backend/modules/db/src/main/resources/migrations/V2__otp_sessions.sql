-- OTP sessions (AUTH-03). Codes stored hashed + peppered; never plaintext.

CREATE TABLE otp_sessions (
    e164_hash       TEXT PRIMARY KEY,
    code_hash       TEXT NOT NULL,
    attempts        INT NOT NULL DEFAULT 0,
    created_at      BIGINT NOT NULL,
    expires_at      BIGINT NOT NULL,
    lockout_until   BIGINT NOT NULL DEFAULT 0,
    resend_after    BIGINT NOT NULL,
    last_sent_at    BIGINT NOT NULL DEFAULT 0,
    consumed_at     BIGINT
);
