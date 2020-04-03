# A multi-stage build Dockerfile to create an image for the Vert.x JPA example.
FROM maven:3-jdk-11 as builder

#Copy the source files into the Maven image and invoke the package goal.
COPY . /opt/app/src
WORKDIR /opt/app/src
RUN mvn package

# Now that the example is built, let's copy the fat JAR from the target
# directory and write it into a JRE image.
FROM adoptopenjdk:11.0.6_10-jre-hotspot
WORKDIR /opt/app
COPY --from=builder /opt/app/src/target/vertx-jwt-idp-1.1-fat.jar .

# Expose port TCP/8080 and set the command
EXPOSE 8443
CMD ["java","-jar","./vertx-jwt-idp-1.1-fat.jar"]