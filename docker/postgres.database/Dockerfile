# Use the official PostgreSQL base image
FROM postgres:15

# Set environment variables for the database and user
ENV POSTGRES_DB=cubetrek
ENV POSTGRES_USER=cubetrek_postgres
ENV POSTGRES_PASSWORD=my-super-secure-password

# Install PostGIS
RUN apt-get update && \
    apt-get install -y postgis postgresql-15-postgis-3 && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Run SQL commands to create the PostGIS extension after the database is ready
COPY ./init-db.sh /docker-entrypoint-initdb.d/init-db.sh
RUN chmod +x /docker-entrypoint-initdb.d/init-db.sh

# Expose the default PostgreSQL port
EXPOSE 5432
