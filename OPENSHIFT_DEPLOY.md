# Deploying Minecraft Plugin to OpenShift

This guide shows how to deploy the Minecraft Dev Commands Plugin to your OpenShift Minecraft server.

## Prerequisites

- Access to OpenShift cluster
- `oc` CLI installed and logged in
- Plugin JAR built (run `./build.sh`)

## Current Deployment

Your current Minecraft setup in OpenShift:
- **Namespace**: `minecraft`
- **Server**: `minecraft-paper` (Paper server on port 25565)
- **Backend**: `minecraft-backend` (API backend on port 8080)
- **Storage**: PersistentVolumeClaim `minecraft-paper-data` mounted at `/data`

## Step 1: Build the Plugin

```bash
cd /path/to/Minecraft_slash_plugin
./build.sh
```

This creates `target/minecraft-devtools-1.0.0.jar`

## Step 2: Set Environment Variables

Create a secret for your API credentials:

```bash
# Login to OpenShift
oc login --token=<your-token> --server=https://api.prod.rhoai.rh-aiservices-bu.com:6443

# Switch to minecraft namespace
oc project minecraft

# Create secret for credentials
oc create secret generic dev-plugin-secrets \
  --from-literal=GITHUB_TOKEN='your-github-token' \
  --from-literal=JIRA_URL='https://yourcompany.atlassian.net' \
  --from-literal=JIRA_EMAIL='your-email@company.com' \
  --from-literal=JIRA_API_TOKEN='your-jira-token'
```

## Step 3: Upload Plugin to Server

There are two methods:

### Method A: Using oc cp (Recommended)

```bash
# Get the pod name
POD=$(oc get pod -l app=minecraft-paper -o jsonpath='{.items[0].metadata.name}')

# Copy plugin to server
oc cp target/minecraft-devtools-1.0.0.jar $POD:/data/plugins/

# Copy config if needed
oc cp src/main/resources/config.yml $POD:/data/plugins/DevCommandsPlugin/config.yml
```

### Method B: Using a ConfigMap

```bash
# Create configmap from JAR (if JAR is small enough, < 1MB)
oc create configmap minecraft-plugin --from-file=target/minecraft-devtools-1.0.0.jar

# Then mount it in the deployment (requires editing deployment)
```

## Step 4: Configure the Plugin

Edit the config file in the server:

```bash
# Get pod name
POD=$(oc get pod -l app=minecraft-paper -o jsonpath='{.items[0].metadata.name}')

# Edit config
oc exec -it $POD -- vi /data/plugins/DevCommandsPlugin/config.yml
```

Or create a ConfigMap for the config:

```bash
# Create config.yml locally with your settings
cat > config.yml <<EOF
github:
  token: "\${GITHUB_TOKEN}"
  repository: "your-org/your-repo"
  api-url: "https://api.github.com"
  project-number: 1

jira:
  url: "\${JIRA_URL}"
  email: "\${JIRA_EMAIL}"
  api-token: "\${JIRA_API_TOKEN}"
  project-key: "PROJ"

vllm:
  url: "http://vllm-service:8000"
  model: "meta-llama/Llama-3-8b-chat-hf"
  max-tokens: 2048
  temperature: 0.7
  timeout: 30

settings:
  debug: false
  max-book-pages: 50
  cache-duration: 300
  max-concurrent-requests: 3
  command-cooldown: 5
EOF

# Create ConfigMap
oc create configmap plugin-config --from-file=config.yml

# Mount in deployment (edit minecraft-paper deployment)
oc edit deployment minecraft-paper
```

Add this to the deployment spec:

```yaml
spec:
  template:
    spec:
      containers:
      - name: minecraft
        envFrom:
        - secretRef:
            name: dev-plugin-secrets  # Add this
        volumeMounts:
        - name: plugin-config
          mountPath: /data/plugins/DevCommandsPlugin/config.yml
          subPath: config.yml
      volumes:
      - name: plugin-config
        configMap:
          name: plugin-config
```

## Step 5: Update Deployment with Secrets

Edit the minecraft-paper deployment to include the secrets:

```bash
oc patch deployment minecraft-paper --type='json' -p='[
  {
    "op": "add",
    "path": "/spec/template/spec/containers/0/envFrom",
    "value": [{"secretRef": {"name": "dev-plugin-secrets"}}]
  }
]'
```

Or manually edit:

```bash
oc edit deployment minecraft-paper
```

Add under `spec.template.spec.containers[0]`:

```yaml
envFrom:
- secretRef:
    name: dev-plugin-secrets
```

## Step 6: Reload Server

Restart the Minecraft pod to load the plugin:

```bash
# Delete the pod (deployment will recreate it)
oc delete pod -l app=minecraft-paper

# Or rollout restart
oc rollout restart deployment/minecraft-paper

# Watch the restart
oc rollout status deployment/minecraft-paper
```

## Step 7: Verify Plugin Loaded

Check the server logs:

```bash
POD=$(oc get pod -l app=minecraft-paper -o jsonpath='{.items[0].metadata.name}')
oc logs -f $POD
```

Look for:
```
[Server thread/INFO]: [DevCommandsPlugin] Enabling DevCommandsPlugin v1.0.0
[Server thread/INFO]: DevCommandsPlugin enabled!
```

## Step 8: Test Commands

Connect to your Minecraft server and test:

```
/jira-list
/kanban-list
/review-pr latest
```

## Updating the Plugin

To update the plugin after making changes:

```bash
# 1. Rebuild
./build.sh

# 2. Get pod name
POD=$(oc get pod -l app=minecraft-paper -o jsonpath='{.items[0].metadata.name}')

# 3. Copy new JAR
oc cp target/minecraft-devtools-1.0.0.jar $POD:/data/plugins/

# 4. Reload plugin in-game
# Run in Minecraft console or as op:
/reload confirm
```

## Troubleshooting

### Plugin not loading
```bash
# Check if JAR exists
oc exec $POD -- ls -la /data/plugins/

# Check server logs
oc logs -f $POD | grep -i "devcommands\|error"
```

### API credentials not working
```bash
# Verify secret exists
oc get secret dev-plugin-secrets

# Check if secret is mounted
oc exec $POD -- env | grep -E "GITHUB|JIRA"
```

### Commands not working
```bash
# Check plugin is enabled
# In Minecraft: /plugins

# Check permissions
# Make sure you're OP: /op YourUsername
```

### vLLM connection issues
```bash
# Check vLLM service exists
oc get svc vllm-service

# Check vLLM pod is running
oc get pods -l app=vllm

# Test connection from Minecraft pod
oc exec $POD -- curl http://vllm-service:8000/health
```

## Architecture Diagram

```
┌─────────────────────────────────────────────────────┐
│  OpenShift Namespace: minecraft                     │
│                                                     │
│  ┌──────────────────┐          ┌────────────────┐  │
│  │ minecraft-paper  │          │ vllm-service   │  │
│  │                  │          │                │  │
│  │ Paper Server     │─────────▶│ AI Model       │  │
│  │ + DevTools Plugin│          │ (port 8000)    │  │
│  │                  │          └────────────────┘  │
│  │ Plugins:         │                              │
│  │  - DevCommands   │                              │
│  │                  │                              │
│  │ Port: 25565      │                              │
│  │ (LoadBalancer)   │                              │
│  └──────────────────┘                              │
│          │                                          │
│          │ PVC                                      │
│          ▼                                          │
│  ┌──────────────────┐                              │
│  │ minecraft-paper- │                              │
│  │ data (PVC)       │                              │
│  │                  │                              │
│  │ /data/plugins/   │                              │
│  │ /data/config/    │                              │
│  └──────────────────┘                              │
│                                                     │
│  External APIs:                                     │
│  • GitHub API (github.com)                          │
│  • Jira API (yourcompany.atlassian.net)            │
└─────────────────────────────────────────────────────┘
```

## Quick Reference

```bash
# Login
oc login --token=<token> --server=https://api.prod.rhoai.rh-aiservices-bu.com:6443
oc project minecraft

# Get pod
POD=$(oc get pod -l app=minecraft-paper -o jsonpath='{.items[0].metadata.name}')

# Upload plugin
oc cp target/minecraft-devtools-1.0.0.jar $POD:/data/plugins/

# View logs
oc logs -f $POD

# Restart server
oc rollout restart deployment/minecraft-paper
```
