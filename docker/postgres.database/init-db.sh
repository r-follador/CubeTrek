#!/bin/bash
set -e

# Modify pg_hba.conf to allow connections for the cubetrek_postgres user
echo "# Allow all connections for the cubetrek_postgres user" >> /var/lib/postgresql/data/pg_hba.conf
echo "host all cubetrek_postgres 0.0.0.0/0 md5" >> /var/lib/postgresql/data/pg_hba.conf

# Modify postgresql.conf to listen on all interfaces
echo "# Listen on all interfaces" >> /var/lib/postgresql/data/postgresql.conf
echo "listen_addresses = '0.0.0.0'" >> /var/lib/postgresql/data/postgresql.conf

# Create the PostGIS extension
psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "CREATE EXTENSION IF NOT EXISTS postgis;"

# Optionally run any additional SQL commands here
# psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /path/to/your/sql/file.sql
