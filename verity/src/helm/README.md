# Verity k8s Deploy

## Publish

```shell
helm package .

aws ecr get-login-password --region ap-southeast-1 | helm registry login --username AWS --password-stdin <ACCOUNT_ID>.dkr.ecr.ap-southeast-1.amazonaws.com
helm push ./verity-<VERSION>.tgz oci://<ACCOUNT_ID>.dkr.ecr.ap-southeast-1.amazonaws.com/
```

## Install

```shell
helm install cas oci://<ACCOUNT_ID>.dkr.ecr.ap-southeast-1.amazonaws.com/verity --version <VERSION> --dry-run
```
