CREATE TABLE portfolio_analytics (
  id      SERIAL  PRIMARY KEY NOT NULL,
  date    DATE  NOT NULL,
  content JSONB NOT NULL,
  unique (date)
);
