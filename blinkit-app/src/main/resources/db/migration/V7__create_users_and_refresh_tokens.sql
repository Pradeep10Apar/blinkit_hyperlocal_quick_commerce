-- =============================================
-- V7: Create users table and refresh_tokens table
-- Purpose: Authentication & Authorization
-- =============================================

-- Enable UUID extension if not exists
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Users table - unified for both OTP and Email/Password auth
CREATE TABLE users (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Common fields
    name                    VARCHAR(100),
    role                    VARCHAR(20) NOT NULL DEFAULT 'USER',
    active                  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP NOT NULL DEFAULT now(),
    last_login_at           TIMESTAMP,
    
    -- OTP Auth fields (Quick Commerce) - for future use
    phone                   VARCHAR(15) UNIQUE,
    phone_verified          BOOLEAN DEFAULT FALSE,
    
    -- Email/Password Auth fields (Enterprise)
    email                   VARCHAR(255) UNIQUE,
    email_verified          BOOLEAN DEFAULT FALSE,
    password_hash           VARCHAR(255),
    failed_login_attempts   INT DEFAULT 0,
    locked_until            TIMESTAMP,
    
    -- Constraints
    CONSTRAINT user_has_identifier CHECK (phone IS NOT NULL OR email IS NOT NULL)
);

-- Indexes for fast lookups
CREATE INDEX idx_users_phone ON users(phone) WHERE phone IS NOT NULL;
CREATE INDEX idx_users_email ON users(email) WHERE email IS NOT NULL;
CREATE INDEX idx_users_role ON users(role);

-- Refresh tokens table for token rotation and revocation
CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    
    -- For tracking
    user_agent      VARCHAR(500),
    ip_address      VARCHAR(50)
);

-- Indexes for refresh tokens
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at) WHERE revoked = FALSE;

-- Comments for documentation
COMMENT ON TABLE users IS 'User accounts supporting both OTP and Email/Password authentication';
COMMENT ON COLUMN users.role IS 'User role: USER, ADMIN';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hashed password for email/password auth';
COMMENT ON COLUMN users.locked_until IS 'Account locked until this time due to failed attempts';
COMMENT ON TABLE refresh_tokens IS 'JWT refresh tokens for token rotation';
