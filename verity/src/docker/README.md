# Docker Image for Verity

# How to Build

From the repository root:

```shell
sbt assembly
sbt verity/k8sDockerPackage

cd verity/verity/target/docker
docker build -t verity .
```

# How to Publish

1.  tag the image
    *   `docker tag verity:latest 609723053065.dkr.ecr.ap-southeast-1.amazonaws.com/verity:<TAG>`
2.  login to the registry
    *   `aws ecr get-login-password --region ap-southeast-1 | docker login --username AWS --password-stdin 609723053065.dkr.ecr.ap-southeast-1.amazonaws.com`
3.  push
    *   `docker push 609723053065.dkr.ecr.ap-southeast-1.amazonaws.com/verity:<TAG>`
