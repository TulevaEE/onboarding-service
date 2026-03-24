---
name: ariregister
description: Ariregister (Estonian Business Registry) SOAP API — fetch WSDL, list operations, describe operation schemas
allowed-tools: Bash(skills/integrations/ariregister/scripts/ariregister.py *)
---

# Ariregister API

Estonian Business Registry (Äriregister) SOAP API at `https://ariregxmlv6.rik.ee/ariregxml`. Authentication via username/password in the SOAP body (not HTTP headers). All XML element names are in Estonian.

## Scripts

### List all operations
```bash
skills/integrations/ariregister/scripts/ariregister.py list
```

### Describe a specific operation (request/response fields, types, annotations)
```bash
skills/integrations/ariregister/scripts/ariregister.py describe <operation_name>
```

### Fetch raw WSDL
```bash
skills/integrations/ariregister/scripts/ariregister.py wsdl
```

### Fetch API documentation (raw HTML with links)
```bash
skills/integrations/ariregister/scripts/ariregister.py docs
```

## Estonian language patterns in XML

- `_tekstina` — human-readable text version of a code field
- `_kpv` — kuupäev (date)
- `keha` — body (request or response payload)
- `paring` — query/request
- `vastus` — response
- `_v1`, `_v2` — API version suffix
