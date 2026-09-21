# Supabase setup

The application now uses Supabase PostgreSQL directly through JDBC.

## Required environment variable

Windows PowerShell:

```powershell
$env:SUPABASE_DB_PASSWORD="<your Supabase database password>"
```

IntelliJ IDEA: Run/Debug Configurations → Environment variables → add
`SUPABASE_DB_PASSWORD`.

## Optional overrides

```text
SUPABASE_DB_URL=jdbc:postgresql://aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres?sslmode=require
SUPABASE_DB_USER=postgres.pgqpfiedfwbblgdxhlcm
```

The database schema is initialized automatically from
`src/main/resources/schema_postgres.sql` on application startup.

## Important

This project is a desktop classroom application. A database-owner password must not be
embedded into a distributed desktop application. For a real multi-user deployment, put a
server/API boundary between the JavaFX client and the database, expose only the needed API,
and use Supabase RLS/service roles appropriately.
