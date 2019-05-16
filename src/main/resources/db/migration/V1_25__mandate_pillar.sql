ALTER TABLE mandate
  ADD COLUMN pillar int;

UPDATE mandate
SET pillar=2;

ALTER TABLE mandate
  alter column pillar SET NOT NULL;
