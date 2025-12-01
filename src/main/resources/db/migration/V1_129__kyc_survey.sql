CREATE TABLE kyc_survey (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    survey JSONB NOT NULL,
    created_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_kyc_survey_user_id_created_time ON kyc_survey(user_id, created_time DESC);
