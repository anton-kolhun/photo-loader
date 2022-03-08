# Photo loader application
Web application that randomly loads photos from a configured aws s3 bucket.

## Required Prerequisites
* JDK 11
* Maven

## Building and Running
```
  mvn install
  AWS_ACCESS_KEY_ID=<your_key> AWS_SECRET_KEY=<your_secret> java -jar surprise-1.0.jar
```

## Accessing the application
After launch should be available at http://localhost:8080


