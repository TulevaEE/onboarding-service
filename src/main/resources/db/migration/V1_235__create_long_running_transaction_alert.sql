CREATE TABLE long_running_transaction_alert (
  pid integer NOT NULL,
  xact_start timestamp with time zone NOT NULL,
  query text NOT NULL,
  created_time timestamp with time zone NOT NULL,
  CONSTRAINT pk_long_running_transaction_alert PRIMARY KEY (pid, xact_start)
);
