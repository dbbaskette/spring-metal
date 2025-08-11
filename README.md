# Demo of Tanzu platform and SpringAI

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.1.2-brightgreen.svg)
![AI LLM](https://img.shields.io/badge/AI-LLM-blue.svg)
![PostgreSQL](https://img.shields.io/badge/postgres-15.1-red.svg)
![Tanzu](https://img.shields.io/badge/tanzu-platform-purple.svg)

This repository contains artifacts necessary to build and run generative AI applications using Spring Boot and Tanzu Platform. 


## Running the Demo

#### Preperations


```bash

mvn clean package

cf login -u admin -p YOUR_CF_ADMIN_PASSWORD
cf target -o YOUR_ORG -s YOUR_SPACE # this space must have access to postgres with pgvector and genai services, multi model is supported

cf push
```


## Contributing
Contributions to this project are welcome. Please ensure to follow the existing coding style and add unit tests for any new or changed functionality.


