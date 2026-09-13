# Jenkins deployment topology

The Jenkins controller runs as the existing Docker container on `ubuntu-server`.
The `school-linux` SSH agent has one executor and performs validation, native
image builds, local containerd import, K3s deployment, and integration tests.
It copies one checksum-verified image archive to the Ubuntu K3s worker before
changing any Deployment.

Required host setup is intentionally outside the pipeline:

- the controller key is stored at
  `/var/jenkins_home/.ssh/school_agent_ed25519` and registered by the init hook;
- the controller and `/home/jenkins/.ssh/known_hosts` on the agent pin the
  authenticated school and `[ssh.github.com]:443` host keys;
- `controller-ssh-config` routes the controller's GitHub SSH connection through
  `school-linux`, so loading the `Jenkinsfile` does not depend on Ubuntu's
  unstable direct Internet path;
- the `jenkins` account on each server uses key-only SSH;
- only `k3s` commands are passwordless for the Jenkins account;
- `school-linux` has Docker group access and one Jenkins executor;
- GitHub webhook may call `/github-webhook/`; SCM polling every five minutes is
  the fallback for a Jenkins server that is not reachable from GitHub.

The pipeline labels images with the 12-character Git commit, disables concurrent
builds, also takes a host-wide native build lock, limits native-image to 12 CPUs
and 12 GiB heap, imports identical images on both nodes, waits for rolling
deployments, runs all black-box tests, and restores the previous image tags when
deployment or integration tests fail. The controller reads `Jenkinsfile` over
the proxied SSH path, while the school agent checks out the public repository
over HTTPS with a bounded retry.

`job.xml` is the controller-side Pipeline job definition. It checks out
`master` through `ssh.github.com:443` with the `github-ssh` credential, loads the
repository `Jenkinsfile`, and polls every five minutes even before the first
successful Pipeline run has registered the GitHub push trigger. Go validation
uses persistent module/build caches and retries transient proxy failures.
