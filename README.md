# FINT Link Walker

Verifies relation links for a given resource.

## Run with Docker Compose

Edit the supplied `docker-compose.yml` to add OAuth credentials, and use `docker-compose up` to start.

Open a browser at http://localhost:8082/swagger-ui.html 

## Usage

Launching this application starts a web server at port `8082` with the following endpoints:

| Path                    | Method    | Description       |
|-------------------------|-----------|-------------------|
| /api/tests/links        | GET       | Get all tests     |
| /api/tests/links        | POST      | Start a new test, returns location header with direct url to the test  |
| /api/tests/links/{id}   | GET       | Get test with id  |

## Model

The POST method requires a JSON object with the following elements:

    {
        "baseUrl": "https://api.felleskomponent.no",
        "endpoint": "/administrasjon/personal/personalressurs",
        "orgId": "pwf.no"
    }
    
| Element  | Description                         |
|----------|-------------------------------------|
| baseUrl  | Base URL for access.                |
| endpoint | Data endpoint to verify.            |
| orgId    | Organization ID to verify data for. |

Base URL can be one of the following:
  - https://api.felleskomponent.no                   
  - https://beta.felleskomponent.no                  
  - https://play-with-fint.felleskomponent.no        


## OAuth 2.0 Environment variables

For protected resources, the following environment variables must be set with valid credentials:

| Variable                      | Content                                           |
|-------------------------------|---------------------------------------------------|
| `fint.oauth.enabled`          | Set to `true` to enable OAuth                     | 
| `fint.oauth.username`         | User Name                                         |
| `fint.oauth.password`         | Password                                          |
| `fint.oauth.access-token-uri` | URI of access token server                        |
| `fint.oauth.client-id`        | OAuth Client ID                                   |
| `fint.oauth.client-secret`    | OAuth Client Secret                               |
| `fint.oauth.scope`            | Set to `fint-client`                              |

Example access token server URI: https://idp.felleskomponent.no/nidp/oauth/nam/token

Example request URL: https://beta.felleskomponent.no/administrasjon/personal
