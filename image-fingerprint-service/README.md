# Image Fingerprinting Service

This is a Spring Boot application that provides an API for calculating image fingerprints (perceptual hashes) and comparing the similarity between two fingerprints.

## Prerequisites

- Java 1.8 JDK
- Maven 3.x

## Building the Project

1.  Navigate to the project's root directory (`image-fingerprint-service`):
    ```bash
    cd path/to/image-fingerprint-service
    ```

2.  Build the project using Maven:
    ```bash
    ./mvnw clean package
    # On Windows, use:
    # mvnw.cmd clean package
    ```
    This will generate a JAR file in the `target` directory (e.g., `image-fingerprint-0.0.1-SNAPSHOT.jar`).

## Running the Application

Once the project is built, you can run the application using:

```bash
java -jar target/image-fingerprint-0.0.1-SNAPSHOT.jar
```

The application will start on the default port `8080`.

## API Endpoints

The API base path is `/api/image`.

### 1. Calculate Image Fingerprint

Upload an image to get its fingerprint.

-   **URL:** `/api/image/fingerprint`
-   **Method:** `POST`
-   **Content-Type:** `multipart/form-data`
-   **Form Parameter:**
    -   `file`: The image file to be processed. Supported formats include common types like PNG, JPEG, GIF, BMP (depending on Java ImageIO capabilities).

-   **Success Response (200 OK):**
    ```json
    {
        "fingerprint": "hexadecimal_fingerprint_string"
    }
    ```

-   **Error Responses:**
    -   `400 Bad Request`: If the file is empty or an invalid argument is provided.
        ```json
        {
            "error": "Error message describing the issue."
        }
        ```
    -   `500 Internal Server Error`: If there's an issue processing the image.
        ```json
        {
            "error": "Failed to process image: Specific error details."
        }
        ```

-   **Example using `curl`:**
    ```bash
    curl -X POST -F "file=@/path/to/your/image.jpg" http://localhost:8080/api/image/fingerprint
    ```

### 2. Calculate Similarity Between Two Fingerprints

Provide two image fingerprints (obtained from the endpoint above) to calculate their similarity.

-   **URL:** `/api/image/similarity`
-   **Method:** `POST`
-   **Content-Type:** `application/json`
-   **Request Body:**
    ```json
    {
        "fingerprint1": "hex_fingerprint_string_1",
        "fingerprint2": "hex_fingerprint_string_2"
    }
    ```

-   **Success Response (200 OK):**
    ```json
    {
        "similarity": 0.85
    }
    ```
    (Similarity is a double value between 0.0 and 1.0, where 1.0 means identical.)

-   **Error Responses:**
    -   `400 Bad Request`: If fingerprints are missing or invalid.
        ```json
        {
            "error": "Error message describing the issue."
        }
        ```
    -   `500 Internal Server Error`: For unexpected issues.
        ```json
        {
            "error": "An unexpected error occurred: Specific error details."
        }
        ```

-   **Example using `curl`:**
    ```bash
    curl -X POST -H "Content-Type: application/json" \
    -d '{"fingerprint1": "your_first_hex_fingerprint", "fingerprint2": "your_second_hex_fingerprint"}' \
    http://localhost:8080/api/image/similarity
    ```

## Notes

-   The hashing algorithm used is AverageHash (aHash) with a 64-bit hash.
-   The similarity score is calculated as `1.0 - normalizedHammingDistance`. A score of `1.0` indicates the images are likely identical according to the hash, while `0.0` indicates they are very different.
-   For production use, consider a more robust logging mechanism than `e.printStackTrace()`.
