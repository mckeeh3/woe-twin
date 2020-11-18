
# Google GKE Installation and Setup

Follow these instructions for installing and running the woe-twin microservice using Google Kubernetes Engine.

## Prerequisites

Clone the weo-twin Github project.

~~~bash
git clone https://github.com/mckeeh3/woe-twin.git
~~~

### Build and Deploy to Google Cloud Container Registry

First, create a GKE (Google Kubernetes Engine) project. From the
[Google Cloud Platform](https://console.cloud.google.com) Dashboard, click The
triple bar icon at the top left and click Kubernetes Engine/Clusters. Follow the
documentation TODO for creating a cluster and a project.

Use the [Quickstart for Container Registry](https://cloud.google.com/container-registry/docs/quickstart)
to create a Docker image container registry.

Deploy [Yugabyte](https://docs.yugabyte.com/latest/deploy/kubernetes/single-zone/gke/helm-chart/) to the GKE cluster.

Build the project, which will create a new Docker image.
~~~bash
$ mvn clean package docker:build
~~~
~~~
...
[INFO]
[INFO] --- docker-maven-plugin:0.26.1:build (default-cli) @ woe-twin ---
[INFO] Copying files to /home/hxmc/Lightbend/akka-java/woe-twin/target/docker/woe-twin/build/maven
[INFO] Building tar: /home/hxmc/Lightbend/akka-java/woe-twin/target/docker/woe-twin/tmp/docker-build.tar
[INFO] DOCKER> [woe-twin:latest]: Created docker-build.tar in 377 milliseconds
[INFO] DOCKER> [woe-twin:latest]: Built image sha256:e8192
[INFO] DOCKER> [woe-twin:latest]: Tag with latest,20200619-124148.ef13797
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  22.986 s
[INFO] Finished at: 2020-06-19T12:48:55-04:00
[INFO] ------------------------------------------------------------------------
~~~

Configure authentication to the Container Registry.
See [Authentication methods](https://cloud.google.com/container-registry/docs/advanced-authentication).
Here the [gcloud as a Docker credential helper](https://cloud.google.com/container-registry/docs/advanced-authentication#gcloud-helper)
method is used.
~~~bash
$ gcloud auth login
~~~

Configure Docker with the following command:
~~~bash
$ gcloud auth configure-docker
~~~

Tag the Docker image.
~~~bash
$ docker tag woe-twin gcr.io/$(gcloud config get-value project)/woe-twin:latest
~~~

Push the Docker image to the ContainerRegistry.
~~~bash
$ docker push gcr.io/$(gcloud config get-value project)/woe-twin:latest
~~~

To view the uploaded container search for "container registry" from the Google Cloud Console.
You can also list the uploaded containers via the CLI.
~~~bash
$ gcloud container images list                    
~~~
~~~
NAME
gcr.io/akka-yuga/woe-twin
Only listing images in gcr.io/akka-yuga. Use --repository to list images in other repositories.
~~~

Create the Kubernetes namespace. The namespace only needs to be created once.
~~~bash
$ kubectl create namespace woe-twin-1                       
~~~
~~~
namespace/woe-twin-1 created
~~~

Set this namespace as the default for subsequent `kubectl` commands.
~~~bash
$ kubectl config set-context --current --namespace=woe-twin-1
~~~
~~~
Context "gke_akka-yuga_us-central1-c_yugadb" modified.
~~~

Deploy the Docker images to the Kubernetes cluster.
~~~bash
$ kubectl apply -f kubernetes/akka-cluster-gke.yml
~~~
~~~
deployment.apps/woe-twin created
role.rbac.authorization.k8s.io/pod-reader created
rolebinding.rbac.authorization.k8s.io/read-pods created
~~~

View the status of the running pods.
~~~bash
$ kubectl get pods   
~~~
~~~
NAME                        READY   STATUS    RESTARTS   AGE
woe-twin-658d9878d9-7zsmv   1/1     Running   0          37m
woe-twin-658d9878d9-bh2jn   1/1     Running   0          37m
woe-twin-658d9878d9-slxmp   1/1     Running   0          37m
~~~

Open a shell on one of the pods.
~~~bash
$ kubectl exec -it woe-twin-658d9878d9-7zsmv -- /bin/bash
~~~
~~~
root@woe-twin-658d9878d9-7zsmv:/# ll maven/woe-twin-1.0-SNAPSHOT.jar
-rw-r--r-- 1 root root 779840 Jul  1 15:28 maven/woe-twin-1.0-SNAPSHOT.jar
root@woe-twin-658d9878d9-7zsmv:/# exit
exit
~~~
#### Scale Running Akka Nodes/K8 pods

~~~bash
$ kubectl scale --replicas=10 deployment/woe-twin
~~~

#### Enable External Access

Create a load balancer to enable access to the WoE Twin microservice HTTP endpoint.

~~~bash
$ kubectl expose deployment woe-twin --type=LoadBalancer --name=woe-twin-service
~~~
~~~
service/woe-twin-service exposed
~~~

~~~bash
$ kubectl get services woe-twin-service                                         
~~~
~~~
NAME               TYPE           CLUSTER-IP     EXTERNAL-IP   PORT(S)                                        AGE
woe-twin-service   LoadBalancer   10.89.15.220   <pending>     2552:30818/TCP,8558:32548/TCP,8080:30812/TCP   20s
~~~

It takes a few minutes for an external IP to be assigned. Note the `EXTERNAL-IP` above eventually changes from `<pending>` to the assigned external IP, shown below.
~~~bash
$ kubectl get services woe-twin-service
~~~
~~~
NAME               TYPE           CLUSTER-IP     EXTERNAL-IP     PORT(S)                                        AGE
woe-twin-service   LoadBalancer   10.89.15.220   34.70.176.161   2552:30818/TCP,8558:32548/TCP,8080:30812/TCP   2m24s
~~~

### Deploy to AWS GKE

At this point a GKE cluster has been created and is ready for deployment. See section *Enable Access to Amazon Elastic Kubernetes Service - EKS* above on how to get started.

Ensure that you have access to the GKE cluster.
~~~bash
$ kubectl get svc
~~~
~~~
NAME         TYPE        CLUSTER-IP   EXTERNAL-IP   PORT(S)   AGE
kubernetes   ClusterIP   10.100.0.1   <none>        443/TCP   8d
~~~

