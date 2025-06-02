CREATE TABLE public.aml_check_comment (
  id BIGSERIAL PRIMARY KEY,
  aml_check_id INTEGER NOT NULL,
  comment_text TEXT NOT NULL,
  created_by TEXT NOT NULL,
  created_time TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
  CONSTRAINT fk_aml_check
    FOREIGN KEY(aml_check_id)
      REFERENCES public.aml_check(id)
      ON DELETE CASCADE
);

CREATE INDEX aml_check_comment_aml_check_id_index
  ON public.aml_check_comment (aml_check_id);

CREATE INDEX aml_check_comment_created_by_index
  ON public.aml_check_comment (created_by);
