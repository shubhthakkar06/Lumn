-- PostgreSQL Schema for Spotify Clone Authentication

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(64) UNIQUE NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    avatar_letter VARCHAR(4) DEFAULT 'U',
    avatar_color_hex VARCHAR(16) DEFAULT '#1ED760',
    is_premium BOOLEAN DEFAULT TRUE,
    is_student BOOLEAN DEFAULT FALSE,
    selected_genres TEXT[] DEFAULT '{}',
    selected_artists TEXT[] DEFAULT '{}',
    has_completed_onboarding BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(LOWER(username));
