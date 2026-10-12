# Use a base image with Java and Gradle
FROM debian:bookworm-slim
MAINTAINER pmffromspace

# Install necessary packages and SDKMAN
RUN apt-get update && \
    apt-get install -y curl unzip zip && \
    curl -s https://get.sdkman.io | bash && \
    /bin/bash -c "source $HOME/.sdkman/bin/sdkman-init.sh && sdk list java && sdk install java 21.0.4-tem && sdk install gradle"

# Set the JAVA_HOME environment variable and add Java to the PATH
ENV JAVA_HOME="/root/.sdkman/candidates/java/current"
ENV PATH="$JAVA_HOME/bin:$PATH"

# Set the working directory
WORKDIR /app

# Copy the Gradle files
COPY build.gradle settings.gradle /app/

# Copy the source code
COPY src /app/src

# Copy the TopoLibrary JAR and Garmin FIT SDK JAR
COPY docker/externaljars/TopoLibrary-2.2-SNAPSHOT.jar /app/libs/
COPY docker/externaljars/fit.jar /app/libs/

# Download dependencies
RUN /bin/bash -c "source $HOME/.sdkman/bin/sdkman-init.sh && gradle --no-daemon dependencies"

# Build the application without running tests
RUN /bin/bash -c "source $HOME/.sdkman/bin/sdkman-init.sh && gradle --no-daemon build -x test"

# Expose the port for the application
EXPOSE 8080

# Set the entry point to run the application
ENTRYPOINT ["java", "-jar", "/app/build/libs/cubetrek-1.1-SNAPSHOT.jar"]
