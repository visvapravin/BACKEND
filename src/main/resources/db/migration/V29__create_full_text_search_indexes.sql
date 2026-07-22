-- Migration V29: PostgreSQL Full-Text Search GIN Indexes for ForumX Search Infrastructure

-- 1. Full-Text Search GIN Index on Questions (title + content)
CREATE INDEX IF NOT EXISTS idx_questions_fts
ON questions USING gin ((setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
                         setweight(to_tsvector('english', coalesce(content, '')), 'B')));

-- 2. Full-Text Search GIN Index on Answers (content)
CREATE INDEX IF NOT EXISTS idx_answers_fts
ON answers USING gin ((to_tsvector('english', coalesce(content, ''))));

-- 3. Full-Text Search GIN Index on Users (username)
CREATE INDEX IF NOT EXISTS idx_users_username_fts
ON users USING gin ((to_tsvector('english', coalesce(username, ''))));
