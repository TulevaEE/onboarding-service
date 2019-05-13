ALTER TABLE fund ADD COLUMN pillar SMALLINT;
UPDATE fund SET pillar = 2 where pillar is null;