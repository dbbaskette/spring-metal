# Demo of Tanzu platform and SpringAI

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.1.2-brightgreen.svg)
![AI LLM](https://img.shields.io/badge/AI-LLM-blue.svg)
![PostgreSQL](https://img.shields.io/badge/postgres-15.1-red.svg)
![Tanzu](https://img.shields.io/badge/tanzu-platform-purple.svg)

This repository contains artifacts necessary to build and run generative AI applications using Spring Boot and Tanzu Platform. The instructions below cover setup for both Cloud Foundry (cf) and Kubernetes (k8s) environments.

## Architecture

![Alt text](https://github.com/0pens0/spring-metal/blob/main/image.png?raw=true "Spring-metal AI topology")

## Prerequisites
- Ensure you have the latest version of the Tanzu CLI installed.
- Access to a Route53 domain and necessary AWS permissions.
- Configured egress settings (closed by default) to connect to external services.


## Running the Demo

#### Preperations

- Update the parameters in ```demo.sh``` according to your TPCF configurations

- cf runtime
```bash
cf login -u admin -p YOUR_CF_ADMIN_PASSWORD
cf target -o YOUR_ORG -s YOUR_SPACE //this space musy have acces to postgres and genai services
```
- k8s runtime

```bash
tanzu build config --build-plan-source-type=file  --build-plan-source [FULL PATH TO spring-metal folder]/.tanzu/build-plan.yml
./demo.sh prepare-k8s
```

#### Deployment

- cf runtime
```bash
cf login -u admin -p YOUR_CF_ADMIN_PASSWORD
cf target -o YOUR_ORG -s YOUR_SPACE
./demo.sh deploy-cf
```
- k8s runtime  
```bash
tanzu login
tanzu context use <my-context>
tanzu project use <my-project>
tanzu space use <my-space>
tanzu deploy
```
note: AI and db external services are bound as part of the deployment. You can bind to on-cluster services by using ```tanzu service create```

### Cleanup

```bash
./demo.sh cleanup-k8s #removes app and services from TPK8s space, but keep the space and its ingress/egress
./demo.sh cleanup-cf #removes app and services from TPCF space
```

### Troubleshooting

#### Issue: Application deployment fails, or stuck in 'deploying'
- **Solution:** In AppsMan->YOUR_SPACE->services->vector db instance->settings: manually enter ```"svc_gw_enable":true``` in the json area and redeploy


## Contributing
Contributions to this project are welcome. Please ensure to follow the existing coding style and add unit tests for any new or changed functionality.


