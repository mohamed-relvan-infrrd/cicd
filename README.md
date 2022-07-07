# polaris-cicd

## base-images

Dockerfiles for base images for building and shipping apps.

## kubeconfigs

Configuration file to connect to EKS.  Does not contain any secret data.

## pipelines

Jenkins pipeline definitions for base images for building and shipping apps.

## Resources

resources for Jenkins shared libraries.

## src

static helper functions for Jenkins shared libraries

## vars

Definitions for Jenkins shared library functions.

### continuousIntegrationPipeline

Shared library function for running all the steps for the CI pipeline for a Java project

Configured in Jenkins as a Global Shared Library.

    ```
     [Manage Jenkins] -> [Configure System] -> [Global Pipeline Libraries]

    Name: polaris-cicd
    Default version: master
    [x] allow default versions to be overridden
    [x] include @library changes in job recent changes

    Retrieval Method
    (*) modern scm
    (*) github
        credentials: github-infrrd-bot
        repository: https://github.com/infer-ai/polaris-cicd

    [default settings]
    ```

## example_jenkinsfile
