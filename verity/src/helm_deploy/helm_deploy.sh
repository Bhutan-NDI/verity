#!/bin/bash
APPDIR="$(dirname -- "$(readlink -f -- "${0}")" )"
if [[ -z "$CI_PROJECT_DIR" ]] ; then
    PROJ_DIR="$APPDIR"
else
    PROJ_DIR="$CI_PROJECT_DIR"
fi

set -x

# # Set the verity version to install from the helm repo
# VERITY_APPLICATION_VERSION="${1:-$VERITY_APPLICATION_VERSION}"

# VERITY_PROJECT_ID=26909221 # This is project id on gitlab.com for Verity
# # Strip off anything up the to the last "/" from the environment name
# #   This turns an environment name like development/dev1 to "dev1"
# #   CI_ENVIRONMENT_NAME comes from gitlab-ci
# ENVIRONMENT_NAME="${CI_ENVIRONMENT_NAME##*/}"
# # Set the cluster agent name we're using in gitlab
# #   For more details see: https://docs.gitlab.com/ee/user/clusters/agent/ci_cd_workflow.html
# CLUSTER_AGENT_NAME=$ENVIRONMENT_NAME

# ### - Main - ###
# ## Pre-check that needed vars are set
# if [[ -z "$CI_ENVIRONMENT_NAME" ]] ; then
#     echo "You must pass CI_ENVIRONMENT_NAME as an env. variable. Is this running outside of a gitlab-ci pipeline??"
#     exit 1
# fi
# if [[ -z "$VERITY_APPLICATION_VERSION" ]] ; then
#     echo "You must pass the verity application version as the first arg OR set VERITY_APPLICATION_VERSION as an env. variable"
#     exit 1
# fi
# if [[ -z "$SSL_CERTIFICATE_ARN" ]] ; then
#     echo "You should pass SSL_CERTIFICATE_ARN as an environment variable, so that the alb ingress will know what to use"
#     exit 1
# fi
# if [[ -z "$VERITY__ENDPOINT__HOST" ]] ; then
#     echo "You should pass VERITY__ENDPOINT__HOST as an environment variable, so that external-dns can create the correct record for the ingress"
#     exit 1
# fi

# # Use the kubectl context that points to our cluster (using gitlab cluster agent)
# kubectl config use-context evernym/gitlab-cluster-agent:${CLUSTER_AGENT_NAME}
# kubectl config set-context --current --namespace=verity-${ENVIRONMENT_NAME}

# # Deploy the verity apps with helm, and our helper script
# #   The following vars come from gitlab-ci:
# #     CI_JOB_TOKEN
# #     CI_API_V4_URL
# #     CI_ENVIRONMENT_NAME

VERITY_APPS=( cas )
# helm repo add --username gitlab-ci-token --password ${CI_JOB_TOKEN} verity "${CI_API_V4_URL}/projects/${VERITY_PROJECT_ID}/packages/helm/stable"

for app in "${VERITY_APPS[@]}" ; do
    YAML_FILE="${PROJ_DIR}/${app}-verity.yaml"
    COMMONYAML_FILE="${PROJ_DIR}/common-verity.yaml"

    helm install iac-${app} ${PROJ_DIR}/../helm \
        --values ${COMMONYAML_FILE} \
        --values ${YAML_FILE} \
        # --set vars.AKKA__MANAGEMENT__CLUSTER__BOOTSTRAP__CONTACT_POINT_DISCOVERY__SERVICE_NAME=${app}-verity-${ENVIRONMENT_NAME} \
        # --set vars.KAMON__ENVIRONMENT__SERVICE=${app} \
        # --set vars.KAMON__ENVIRONMENT__TAG__ENV=${ENVIRONMENT_NAME} \
        # --set vars.SSL_CERTIFICATE_ARN=${SSL_CERTIFICATE_ARN} \
        # --set vars.VERITY__ENDPOINT__HOST=${VERITY__ENDPOINT__HOST} \
        # --set vars.VERITY__METRICS__SERVICE_NAME=${app} \
        # --set vars.VERITY__METRICS__TAGS__ENV=${ENVIRONMENT_NAME} \
        # --set gitlab_env=${CI_ENVIRONMENT_NAME} \
        # --set deployment.tag=${VERITY_APPLICATION_VERSION} \
        # --set version=${VERITY_APPLICATION_VERSION}
done

# This will wait until all of the deployments have completely rolled out.
# If there is an error then kubectl will exit non-zero. In the future
# maybe we could have it trigger a rollback? Like this:
#   kubectl rollout undo deployment ${app}-verity-${ENVIRONMENT_NAME}
#
# See also: https://polarsquad.com/blog/check-your-kubernetes-deployments
# for app in "${VERITY_APPS[@]}" ; do
#   kubectl rollout status deployment ${app}-verity-${ENVIRONMENT_NAME} --timeout=20m || exit 1 ;
# done
