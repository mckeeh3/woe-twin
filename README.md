
# Where On Earth Twin (woe-twin) Microservice

TODO

### Design notes

#### Map Regions

Map regions are a rectangular area on a global map. Regions are bounded
by a top left corner, and a bottom right corner. Corners are defined by latitude
and longitude values.

Map regions are viewed as rectangular tiles when viewed on global maps.
Each map region may be selected to contain IoT devices.

Maps are viewed at various zoom levels, from zoom level 0 to 18. Level 0
represents the entire global map, which is 180 degrees from top to bottom
and 360 degrees from left to right. Top to bottom degrees are the latitudes
and left to right degrees are longitudes.

#### Map Region Selections

Map selections are rectangular areas that are bounded by a top left
corner, and a bottom left corner. Each new map selection is submitted to
a map at the top zoom level.

Each new map selection traverses each map zoom
level to determine what map regions it overlaps. As a map selection
moves through each zoom level the map regions check it to determine if
the selection overlaps the region. When a given map selection does not
overlap a map region the zoom traversal stops. When there is an overlap
the map region determines if the map selection fully or partially overlaps
the map region.

Each fully or partially overlapping  map selection is recorded by the map
region. Recorded map selections are trimmed to fall completely within the
map region.

Once overlaping map selections have been recorded the map will then pass
the map selection on to map sub regions. Typically, each map region
contains 4 sub regions. This recursion starts at zoom level 0 and continues
to zoom level 18.

### How to Deploy the WoE Simulator Microservice

- [Minikube with Yugabyte DB](README-minikube-yugabyte.md)
- Gooke Kubernetes Engine with Yugabyte DB (TODO)
- AWS Elastic Kubernetes Service (TODO)
---

**Note**

The following documentation is being replaced with environment specific README documents.

---

### How to Deploy the WoE Twin Microservice

The `kubectl` CLI provides a nice Kubectl Autocomplete feature for `bash` and `zsh`.
See the [kubectl Cheat Sheet](https://kubernetes.io/docs/reference/kubectl/cheatsheet/#kubectl-autocomplete) for instructions.

Also, consider installing [`kubectx`](https://github.com/ahmetb/kubectx), which also includes `kubens`.
Mac:
~~~bash
$ brew install kubectx
~~~
Arch Linux:
~~~bash
$ yay kubectx
~~~

#### Enable Access to Google Kubernetes Engine - GKE

TODO

#### Enable Access to Amazon Elastic Kubernetes Service - EKS

Go to [Getting started with eksctl](https://docs.aws.amazon.com/eks/latest/userguide/getting-started-eksctl.html)
for directions on setting up EKS and Kubernetes CLI tools.

See AWS EKS sections below for specific instructions on setting up Cassandra and deploying the woe-sim microserivce to an EKS cluster.

#### Yugabyte on Kubernetes or MiniKube

Follow the documentation for installing Kubernetes,
[MiniKube](https://kubernetes.io/docs/tasks/tools/install-minikube/)
and
[Yugabyte](https://docs.yugabyte.com/latest/deploy/).

Recommended default deployment changes.
* Deploy with [Helm](https://docs.yugabyte.com/latest/deploy/kubernetes/single-zone/gke/helm-chart/)
* Use namespace `yugabyte-db`. `kubectl create namespace yugabyte-db`
* Specify Kubernetes pod replicas, CPU request and limit when doing the `hrlm install` step.

~~~bash
$ helm install yugabyte-db yugabytedb/yugabyte --namespace yugabyte-db --wait \
--set replicas.tserver=4,\
resource.tserver.requests.cpu=4,\
resource.tserver.limits.cpu=8
~~~

As shown in the Yugabyte documentation, verify the status of the deployment using the following command.
~~~bash
$ helm status yugabyte-db -n yugabyte-db
~~~
~~~
NAME: yugabyte-db
LAST DEPLOYED: Mon Jul 27 14:36:03 2020
NAMESPACE: yugabyte-db
STATUS: deployed
REVISION: 1
TEST SUITE: None
NOTES:
1. Get YugabyteDB Pods by running this command:
  kubectl --namespace yugabyte-db get pods

2. Get list of YugabyteDB services that are running:
  kubectl --namespace yugabyte-db get services

3. Get information about the load balancer services:
  kubectl get svc --namespace yugabyte-db

4. Connect to one of the tablet server:
  kubectl exec --namespace yugabyte-db -it yb-tserver-0 bash

5. Run YSQL shell from inside of a tablet server:
  kubectl exec --namespace yugabyte-db -it yb-tserver-0 -- /home/yugabyte/bin/ysqlsh -h yb-tserver-0.yb-tservers.yugabyte-db

6. Cleanup YugabyteDB Pods
  For helm 2:
  helm delete yugabyte-db --purge
  For helm 3:
  helm delete yugabyte-db -n yugabyte-db
  NOTE: You need to manually delete the persistent volume
  kubectl delete pvc --namespace yugabyte-db -l app=yb-master
  kubectl delete pvc --namespace yugabyte-db -l app=yb-tserver
~~~

#### Create Cassandra and PostgreSQL Tables

Try the following commands to verify access to Cassandra CQL and PostgreSQL
CQL CLI tools once Yugabyte has been installed in a Kubernetes environment.

Cassandra CQL shell
~~~bash
$ kubectl --namespace yugabyte-db exec -it yb-tserver-0 -- /home/yugabyte/bin/ycqlsh yb-tserver-0
~~~
~~~
Defaulting container name to yb-tserver.
Use 'kubectl describe pod/yb-tserver-0 -n yugabyte-db' to see all of the containers in this pod.
Connected to local cluster at yb-tserver-0:9042.
[ycqlsh 5.0.1 | Cassandra 3.9-SNAPSHOT | CQL spec 3.4.2 | Native protocol v4]
Use HELP for help.
ycqlsh> quit
~~~

PostgreSQL shell
~~~bash
$ kubectl --namespace yugabyte-db exec -it yb-tserver-0 -- /home/yugabyte/bin/ysqlsh -h yb-tserver-0  --echo-queries
~~~
~~~
Defaulting container name to yb-tserver.
Use 'kubectl describe pod/yb-tserver-0 -n yugabyte-db' to see all of the containers in this pod.
ysqlsh (11.2-YB-2.2.0.0-b0)
Type "help" for help.

yugabyte=# quit
~~~

##### Copy CQL and SQL DDL commands to the Yugabyte server

From the woe-twin project directory.

~~~bash
$ kubectl cp src/main/resources/akka-persistence-journal-create-twin.cql yugabyte-db/yb-tserver-0:/tmp                                                                  
Defaulting container name to yb-tserver.

$ kubectl cp src/main/resources/region-projection.sql yugabyte-db/yb-tserver-0:/tmp
Defaulting container name to yb-tserver.

$ kubectl cp src/main/resources/akka-projection-offset-store.sql yugabyte-db/yb-tserver-0:/tmp
Defaulting container name to yb-tserver.
~~~

##### Create the Cassandra and PostgreSQL Tables
Cassandra
~~~bash
$ kubectl --namespace yugabyte-db exec -it yb-tserver-0 -- /home/yugabyte/bin/ycqlsh yb-tserver-0                                                      
~~~
~~~
Defaulting container name to yb-tserver.
Use 'kubectl describe pod/yb-tserver-0 -n yugabyte-db' to see all of the containers in this pod.
Connected to local cluster at yb-tserver-0:9042.
[ycqlsh 5.0.1 | Cassandra 3.9-SNAPSHOT | CQL spec 3.4.2 | Native protocol v4]
Use HELP for help.
~~~
~~~
ycqlsh> source '/tmp/akka-persistence-journal-create-twin.cql'
ycqlsh> describe keyspaces;
~~~
~~~
system_schema  woe_twin  system_auth  system
~~~
~~~
ycqlsh> use woe_twin;
ycqlsh:woe_twin> describe tables;
~~~
~~~
tag_views  tag_scanning         tag_write_progress
messages   all_persistence_ids  metadata          
~~~
~~~
ycqlsh:woe_twin> quit
~~~

PostgreSQL
~~~bash
$ kubectl --namespace yugabyte-db exec -it yb-tserver-0 -- /home/yugabyte/bin/ysqlsh -h yb-tserver-0  --echo-queries
~~~
~~~
Defaulting container name to yb-tserver.
Use 'kubectl describe pod/yb-tserver-0 -n yugabyte-db' to see all of the containers in this pod.
ysqlsh (11.2-YB-2.1.8.1-b0)
Type "help" for help.
~~~
~~~
yugabyte=# \i /tmp/region-projection.sql
~~~
~~~
create table if not exists region (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
) split into 20 tablets;
CREATE TABLE
~~~
~~~
yugabyte=# \i /tmp/akka-projection-offset-store.sql
~~~
~~~
create table if not exists "AKKA_PROJECTION_OFFSET_STORE" (
  "PROJECTION_NAME" VARCHAR(255) NOT NULL,
  "PROJECTION_KEY" VARCHAR(255) NOT NULL,
  "OFFSET" VARCHAR(255) NOT NULL,
  "MANIFEST" VARCHAR(4) NOT NULL,
  "MERGEABLE" BOOLEAN NOT NULL,
  "LAST_UPDATED" BIGINT NOT NULL,
  constraint "PK_PROJECTION_ID" primary key ("PROJECTION_NAME","PROJECTION_KEY")
);
CREATE TABLE
create index if not exists "PROJECTION_NAME_INDEX" on "AKKA_PROJECTION_OFFSET_STORE" ("PROJECTION_NAME");
CREATE INDEX
~~~
~~~
yugabyte=# \q
~~~

### Build and Deploy to MiniKube

From the woe-twin project directory.

Before the build, set up the Docker environment variables using the following commands.
~~~bash
$ minikube docker-env
~~~
~~~
export DOCKER_TLS_VERIFY="1"
export DOCKER_HOST="tcp://192.168.99.102:2376"
export DOCKER_CERT_PATH="/home/hxmc/.minikube/certs"
export MINIKUBE_ACTIVE_DOCKERD="minikube"

# To point your shell to minikube's docker-daemon, run:
# eval $(minikube -p minikube docker-env)
~~~
Copy and paster the above `eval` command.
~~~bash
$ eval $(minikube -p minikube docker-env)
~~~

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

Add the local docker image into MiniKube.
~~~bash
$ minikube cache add woe-twin:latest
$ minikube cache list
~~~
~~~              
woe-twin:latest
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
Context "minikube" modified.
~~~

Deploy the Docker images to the Kubernetes cluster.
~~~bash
$ kubectl apply -f kubernetes/akka-cluster.yml
~~~
~~~
deployment.apps/woe-twin created
role.rbac.authorization.k8s.io/pod-reader created
rolebinding.rbac.authorization.k8s.io/read-pods created
~~~
Check if the pods are running. This may take a few moments.
~~~bash
$ kubectl get pods                                          
~~~
~~~
NAME                      READY   STATUS    RESTARTS   AGE
woe-twin-746587fbf4-2zth5   1/1     Running   0          33s
woe-twin-746587fbf4-trkdt   1/1     Running   0          33s
woe-twin-746587fbf4-zzk7f   1/1     Running   0          33s
~~~

#### Enable External Access

Create a load balancer to enable access to the WoE Twin microservice HTTP endpoint.

~~~bash
$ kubectl expose deployment woe-twin --type=LoadBalancer --name=woe-twin-service
~~~
~~~
service/woe-twin-service exposed
~~~

Next, view to external port assignments.

~~~bash
$ kubectl get services woe-twin-service
~~~
~~~
NAME               TYPE           CLUSTER-IP      EXTERNAL-IP   PORT(S)                                        AGE
woe-twin-service   LoadBalancer   10.106.25.172   <pending>     2552:31029/TCP,8558:32559/TCP,8080:32171/TCP   5h4m
~~~

Note that in this example, the Kubernetes internal port 8080 external port assignment of 32171.

For MiniKube deployments, the full URL to access the HTTP endpoint is constructed using the MiniKube IP and the external port.

~~~bash
$ minikube ip       
~~~
In this example the MiniKube IP is:
~~~
192.168.99.102
~~~
Try accessing this endpoint using the curl command or from a browser.
~~~bash
$ curl -v http://$(minikube ip):32171
~~~
~~~
*   Trying 192.168.99.102:32171...
* Connected to 192.168.99.102 (192.168.99.102) port 32171 (#0)
> GET / HTTP/1.1
> Host: 192.168.99.102:32171
> User-Agent: curl/7.70.0
> Accept: */*
>
* Mark bundle as not supporting multiuse
< HTTP/1.1 200 OK
< Last-Modified: Thu, 18 Jun 2020 14:31:18 GMT
< ETag: "52800172c7d756f0"
< Accept-Ranges: bytes
< Server: akka-http/10.1.12
< Date: Fri, 19 Jun 2020 00:26:36 GMT
< Content-Type: text/html; charset=UTF-8
< Content-Length: 330
<
<!DOCTYPE html>
<html lang="en">

<head>
  <title>Of Things Internet</title>
  <script src="p5.js" type="text/javascript"></script>
  <script src="mappa.js" type="text/javascript"></script>
  <script src="woe.js" type="text/javascript"></script>
  <style> body { padding: 0; margin: 0; }</style>
</head>

<body>
</body>

</html>

* Connection #0 to host 192.168.99.102 left intact
~~~

#### Verify Internal HTTP access
The WoE Twin and WoE Sim microservices communicate with each other via HTTP. Each
microservie needs to know the host name of the other service. Use the following to
verify the hostname of this service.

First, get the IP assigned to the load balancer.
~~~bash
$ kubectl get service woe-twin-service
~~~
~~~
NAME               TYPE           CLUSTER-IP      EXTERNAL-IP   PORT(S)                                        AGE
woe-twin-service   LoadBalancer   10.106.25.172   <pending>     2552:31029/TCP,8558:32559/TCP,8080:32171/TCP   16h~~~
~~~
In this example, the internal load balancer IP is 10.106.225.172.

Next, run a shell that can be used to look around the Kubernetes network.
~~~bash
$ kubectl run -i --tty --image busybox:1.28 dns-test --restart=Never --rm
~~~
Use the nslookup command to see the DNS names assigned to the load balancer IP.
~~~
/ # nslookup 10.106.25.172
Server:    10.96.0.10
Address 1: 10.96.0.10 kube-dns.kube-system.svc.cluster.local

Name:      10.106.25.172
Address 1: 10.106.25.172 woe-twin-service.woe-twin-1.svc.cluster.local
~~~
Note that the load balancer host name is `woe-twin-service.woe-twin-1.svc.cluster.local`.

Verify that the WoE Twin HTTP server is accessible via the host name.
~~~
/ # wget -qO- http://woe-twin-service.woe-twin-1.svc.cluster.local:8080
<!DOCTYPE html>
<html lang="en">

<head>
  <title>Of Things Internet</title>
  <script src="p5.js" type="text/javascript"></script>
  <script src="mappa.js" type="text/javascript"></script>
  <script src="woe.js" type="text/javascript"></script>
  <style> body { padding: 0; margin: 0; }</style>
</head>

<body>
</body>

</html>
~~~
Leave the shell using the `exit` command.
~~~
/ # exit
pod "dns-test" deleted
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

#### Create Keyspaces Cassandra tables

Go to the [Akazon Keyspaces](https://console.aws.amazon.com/keyspaces/home?region=us-east-1#keyspaces) and click `Create keyspace` at top right.

Keyspace name: `woe_simulator` then click `Create keyspace`.

Use the [CQL editor](https://console.aws.amazon.com/keyspaces/home?region=us-east-1#cql-editor)
or follow the steps for installing the `cqlsh` at
[Using cqlsh to connect to Amazon Keyspaces (for Apache Cassandra)](https://docs.aws.amazon.com/keyspaces/latest/devguide/programmatic.cqlsh.html).

The DDL file used to create the tables is located in the woe-twin project at `src/main/resources/akka-persistence-journal-create-twin.cql`.
