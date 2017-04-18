CREATE TABLE member (
  id SERIAL PRIMARY KEY,
  user_id integer NOT NULL REFERENCES users,
  member_number integer NOT NULL UNIQUE,
  created_date timestamp without time zone NOT NULL);

INSERT INTO member (user_id, member_number, created_date)
	SELECT id, member_number, created_date
		FROM USERS;

ALTER TABLE users DROP COLUMN member_number;