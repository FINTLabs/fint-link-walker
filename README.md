# FINT Link Walker

Verifies relation links for a given resource.

## Run locally

Pull the repository and run the application through IntelliJ or by building the Jar file with gradle.

## Usage

Launching this application starts a web server at port `8080` with the following endpoints:

| Path                            | Method | Description                                                           |
|---------------------------------|--------|-----------------------------------------------------------------------|
| `/link-walker/tasks/{org}`      | POST   | Start a new task, returns location header with direct url to the task |
| `/link-walker/tasks/{org}`      | GET    | Get all tasks                                                         |
| `/link-walker/tasks/{org}`      | PUT    | Clear all tasks                                                       |
| `/link-walker/tasks/{org}/{id}` | GET    | Displays the specific task                                            |
| `/link-walker/tasks/{org}/{id}` | PUT    | Clear specific task                                                   |

### Display results

| Path                             | Method | Description                            |
|----------------------------------|--------|----------------------------------------|
| `/link-walker/report/{org}`      | GET    | Get all reports for tasks thats active |
| `/link-walker/report/{org}/{id}` | GET    | Get report of specific task            |


## Model

The POST method requires a JSON object with the following elements:

    {
        "url": "https://api.felleskomponent.no/utdanning/elev/elev",
        "clientName": "name@client.no"
    }
    
| Element    | Description                                             |
|------------|---------------------------------------------------------|
| url        | Data endpoint to verify.                                |
| clientName | <optional> required if you do not have bearer token set |
