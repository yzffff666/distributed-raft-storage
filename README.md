# Distributed Raft Storage

A distributed storage project around the Raft consensus model, focused on architecture organization and engineering integration.

## Project Overview

This repository presents a Raft-oriented storage system with modular service design, protocol separation, and deployment-facing project layout.

## Repository Structure

- `spring-boot-api/`: API layer and service bootstrap (Spring Boot)
- `distribute-java-core/`: core distributed logic and shared components
- `distribute-java-protocol/`: protocol and serialization definitions
- `helm/`: Helm chart assets for cluster deployment
- `k8s/`: Kubernetes manifests and related resources
- `ci-cd/`: CI/CD related configuration artifacts

## Tech Stack

- Java
- Spring Boot
- Maven
- Protocol Buffers
- Redis (for selected runtime integrations)
- Docker / Kubernetes / Helm

## My Engineering Focus

- Integrated and adjusted module structure for end-to-end project flow
- Adapted service configuration and runtime parameters for local environment use
- Organized repository for engineering delivery (build/deploy/config split)
- Added and trimmed project materials for external repository publishing

## Notes

- This repository is maintained mainly for architecture study and engineering workflow presentation.
- Environment-dependent runtime details are intentionally not documented as a standard setup guide.
