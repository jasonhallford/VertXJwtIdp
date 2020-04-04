## Introduction
This repository contains example code for a simple JWT identity provider built with [Eclipse Vert.x](http://vertx.io). It
demonstrates the following
* How to configure the Vert.x HTTP server to support TLS
* How to read multipart/form-data fields
* How to generate a JWT the complies with [IETF RFC 7519](https://tools.ietf.org/html/rfc7519)

## Requirements
To build this example, you will need the following:
1. A version of Git capable of cloning this repository from Git Hub
1. Apache Maven v3.5 or greater
1. The latest patch release of OpenJDK 11 (build produced by the [AdoptOpenJDK Project](https://adoptopenjdk.net/) work
nicely)

## Building the Project
You may build the example in one of two ways, as a JAR or a Docker image. 
### Maven Build
You may build JAR from source using [Apache Maven](http://maven.apache.org). Assuming a version >= 3.5.0 you can build it  by
executing `mvn package` at the command line (assuming `mvn` is in the path, of course). In the project's /target
directory, this will produce
* A JAR file named __vertx-jwt-idp-1.3.jar__, which contains just the project's classes
* A fat JAR named __vertx-jwt-idp-1.3-fat.jar__; you can use this to run the code by executing `java -jar VertXJpa-1.0-fat.jar`
at your favorite command line
### Building as a Docker Image
You may use the included Dockerfile to create a deployable image. From the source directory, run the following
command to build the image: `docker build -t vertxjwtidp:1.3 .`. Here, the resulting image will have the tag
__vertxjwtidp:1.3__. 

Run the container with the following command: `docker run --rm -p 8080:8080 --name vertxjwtidp vertxjwtidp:1.3`. You will 
be able to connect to the app at https://localhost:8443.

## Configuring the Example
The example includes a default configuration that creates an API verticle bound to port TCP/8443

The TCP port and other behaviors may be customized by setting the following properties either as OS environment
variables or JRE system proprties (i.e. "-D" properties). The latter have a higher priority than the former.
| Property          | Notes                                                        |
| ----------------- | ------------------------------------------------------------ |
| bind-port         | An integer value that sets the API verticle's TCP bind port. |
| idp-keystore      | The absolute path to a Java key store (.jks) containing a TLS private key and certificate.  This parameter is required! |
| idp-keystore-password | The password for idp-key-store. | 

## Running the Example
Unless configured otherwise, the application presents a single RESTful endpoint on port TCP/8443 that will issue a 
signed JWT. POST a request to https://localhost:8443/api/oauth2/token with the following attributes:
* Content-Type: application/x-www-form-urlencoded
* A body with the following form fields:
    * client_id = 6fe630e9-7e07-4ceb-9887-41e195a07917 (this client is pre-configured)
    * client_secret = a41a9717-3632-4c75-9806-5ccb6b66f8d6 (the pre-configured client's password)
    * grant_type = client_credentials
    
If all goes well, you will receive an application/json response that looks like this:
```json
{
    "access_token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ2ZXJ0eGp3dCIsInN1YiI6IjZmZTYzMGU5LTdlMDctNGNlYi05ODg3LTQxZTE5NWEwNzkxNyIsImlhdCI6IjIwMjAtMDQtMDNUMjE6NTk6MDAuMzcwNTY2WiIsIm5iZiI6IjIwMjAtMDQtMDNUMjE6NTk6MDAuMzcwNTY2WiIsImp0aSI6IjkxYmQ4NzcyLTZiYTgtNDVjYy1iZWU5LTM2NmIwYjI3NDg1MSIsImV4cCI6IjIwMjAtMDQtMDNUMjI6NTk6MDAuMzcwNTY2WiJ9.WdgTLGwJWQkvXC9VkqPYks5WuTtS70Sii5H1nYStWCHVvCyoU2uYvC_4dYSYMVdEnO1aZKQL7fzNfol_qYqzQ8TwekV9TwrafjjKQ0DLqgOs9SUvb2hXSDMDlzbII5_T1IUM_lw5DMLC-gl4rxINa3ywdDXAC9-x70wxAAScHsK1cINvy_y8w5kSjIhLJZaSBVRk-a7A-0FwOYdN1MN-kKGMHlKEDQWrA8Xja2JfRs1tTITTL5p5MZB2XcYBo4H8QH85ww_G81_QUcxaKcb8ObIJS3JBouwsLf-T0U0531qxxyCUQzRJWp4TVTP87448LN4_YrMf_ZU9aeOOM7cVBw",
    "token_type": "bearer",
    "expires_in": 3600
}
```
I recommend [Postman](https://www.postman.com/) to exercise the example, although any tool capable of generating the
necessary HTTP requests will suffice.

You may extract the value of access_token and use it in the Authorization header as a bearer token, assuming, of course, that
the receiver is configured to accept tokens from the 'vertxjwt' issuer. 
