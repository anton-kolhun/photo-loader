# Photo loader application
Web application that randomly loads photos from a configured aws s3 bucket.

## Required Prerequisites
* JDK 11+
* Maven

## Building 
```
  mvn install
```

## Running The Web Application
```
  AWS_ACCESS_KEY_ID=<your_key> AWS_SECRET_KEY=<your_secret> java -jar photo-webapp/target/photo-webapp-1.0-exec.jar
```

## Accessing The Web Application
After launch should be available at http://localhost:8080

## Running The File Sorter

This service scans all the images in a parent directory (s3://<your_bucket>/), checks date taken of the photos (over EXIF)
and moves them to subdirectories as :
`image.jpeg -> <date_taken_get_year>/image.jpeg`.
E.g. `image.jpeg -> 2020/image.jpeg`.

```
  AWS_ACCESS_KEY_ID=<your_key> AWS_SECRET_KEY=<your_secret> java -jar photo-sorter/target/photo-sorter.jar
```


