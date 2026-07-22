-- Enable pg_trgm extension for trigram matching
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Create GIN trigram indexes for performant partial text searches on title and content
CREATE INDEX IF NOT EXISTS idx_questions_title_trgm ON questions USING gin (title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_questions_content_trgm ON questions USING gin (content gin_trgm_ops);
