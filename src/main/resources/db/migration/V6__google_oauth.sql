-- Allow nullable password_hash to support Google OAuth accounts
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- Add google_id column
ALTER TABLE users ADD COLUMN google_id VARCHAR(255);
ALTER TABLE users ADD CONSTRAINT uk_users_google_id UNIQUE (google_id);

-- Create index on google_id
CREATE INDEX idx_users_google_id ON users (google_id);
