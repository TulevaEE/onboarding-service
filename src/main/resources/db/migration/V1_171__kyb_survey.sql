CREATE TABLE kyb_survey (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    registry_code TEXT NOT NULL,
    survey JSONB NOT NULL,
    created_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_kyb_survey_user_id_created_time ON kyb_survey(user_id, created_time DESC);
