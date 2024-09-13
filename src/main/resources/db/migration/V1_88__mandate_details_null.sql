UPDATE mandate SET details = null,
                   mandate_type = 'UNKNOWN'
               WHERE details = '{}' AND
                     mandate_type is null;
