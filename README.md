# Minecraft Dev Commands Plugin

A Paper plugin that brings GitHub PR reviews, Jira ticket management, Kanban boards, and AI assistance directly into Minecraft!

## Features

### Git/GitHub Commands (All under `/git`)
- `/git <owner/repo>` - Change the GitHub repository (e.g., `/git vllm-project/vllm`)
- `/git <github-url>` - Change repository using a full URL (e.g., `/git https://github.com/anthropics/anthropic-sdk-python`)
- `/git repo` - Show the current GitHub repository
- `/git pr-list` - List all open pull requests
- `/git pr-review <latest|number>` - Get an AI-powered review of a pull request in a Minecraft book
- `/git kanban-list` - List all GitHub projects for the repository
- `/git kanban-view <project-number>` - View a GitHub project kanban board in a book

### Jira Integration
- `/jira-create <type> <summary> [| description]` - Create a new Jira issue (Bug, Task, Story)
- `/jira-view <issue-key>` - View a Jira issue in a book
- `/jira-list [mine|bugs|all]` - List Jira issues (defaults to open issues)

### AI Assistant
- `/ai-chat <message>` - Chat with an AI assistant
- `/code-explain <file>` - Get AI explanation of code from your repository

## Architecture

```
┌─────────────────────────────────┐
│   OpenShift Namespace           │
│                                 │
│  ┌──────────────┐              │
│  │ Minecraft    │              │
│  │ Paper Server │──────┐       │
│  │ + Plugin     │      │       │
│  └──────────────┘      │       │
│                        ▼       │
│                  ┌──────────┐  │
│                  │  vLLM    │  │
│                  │  Server  │  │
│                  │ (Service)│  │
│                  └──────────┘  │
└─────────────────────────────────┘
```

## Setup

### 1. Build the Plugin

```bash
mvn clean package
```

This creates `target/dev-commands-plugin-1.0.0.jar`

### 2. Install on Server

Copy the JAR to your Paper server's `plugins/` directory:

```bash
cp target/dev-commands-plugin-1.0.0.jar /path/to/server/plugins/
```

### 3. Configure

Edit `plugins/DevCommandsPlugin/config.yml`:

#### GitHub Configuration

```yaml
github:
  token: "${GITHUB_TOKEN}"  # Or hardcode your token (not recommended)
  repository: "cedricclyburn/minecraft-for-standup"
  api-url: "https://api.github.com"
```

**Getting a GitHub Token:**
1. Go to GitHub Settings > Developer Settings > Personal Access Tokens
2. Generate new token (classic)
3. Select scopes: `repo` (for private repos) or just `public_repo`
4. Set as environment variable: `export GITHUB_TOKEN=ghp_xxxxx`

#### Jira Configuration

```yaml
jira:
  url: "${JIRA_URL}"  # Your Jira instance (e.g., https://yourcompany.atlassian.net)
  email: "${JIRA_EMAIL}"  # Your Jira email
  api-token: "${JIRA_API_TOKEN}"  # Generate at https://id.atlassian.com/manage-profile/security/api-tokens
  project-key: "PROJ"  # Your default project key
```

**Getting a Jira API Token:**
1. Go to https://id.atlassian.com/manage-profile/security/api-tokens
2. Click "Create API token"
3. Give it a label (e.g., "Minecraft Plugin")
4. Copy the token and set as environment variable: `export JIRA_API_TOKEN=your_token_here`

#### GitHub Projects Configuration

Uses the same GitHub token as PR reviews. Just set the default project number:

```yaml
github:
  project-number: 1  # Your default project board number
```

#### vLLM Configuration

```yaml
vllm:
  url: "http://vllm-service:8000"  # Your vLLM service URL
  model: "meta-llama/Llama-3-8b-chat-hf"
  max-tokens: 2048
  temperature: 0.7
  timeout: 30
```

### 4. Deploy vLLM in OpenShift

Create a vLLM deployment in your OpenShift namespace:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vllm
spec:
  replicas: 1
  selector:
    matchLabels:
      app: vllm
  template:
    metadata:
      labels:
        app: vllm
    spec:
      containers:
      - name: vllm
        image: vllm/vllm-openai:latest
        args:
          - --model
          - meta-llama/Llama-3-8b-chat-hf
          - --host
          - 0.0.0.0
          - --port
          - "8000"
        ports:
        - containerPort: 8000
        resources:
          requests:
            memory: "8Gi"
            cpu: "4"
          limits:
            memory: "16Gi"
            cpu: "8"
---
apiVersion: v1
kind: Service
metadata:
  name: vllm-service
spec:
  selector:
    app: vllm
  ports:
  - protocol: TCP
    port: 8000
    targetPort: 8000
```

Apply with:
```bash
oc apply -f vllm-deployment.yaml
```

### 5. Set Environment Variables (Recommended)

In your Minecraft server startup script or OpenShift deployment:

```bash
export GITHUB_TOKEN=ghp_your_token_here
java -jar paper.jar
```

Or in OpenShift, create a secret:

```bash
oc create secret generic github-token --from-literal=GITHUB_TOKEN=ghp_xxxxx
```

Then mount it in your Minecraft server deployment.

## Usage Examples

### Git/GitHub Commands

#### Change Repository
```
/git vllm-project/vllm
```
Changes the repository to vllm-project/vllm

```
/git https://github.com/anthropics/anthropic-sdk-python
```
Changes repository using a full GitHub URL

#### Show Current Repository
```
/git repo
```
Displays the current GitHub repository being queried

#### List Pull Requests
```
/git pr-list
```
Gives you a book listing all open pull requests. Book title includes the repo name.

#### Review Pull Requests
```
/git pr-review latest
```
Fetches the most recent open PR, sends it to vLLM for analysis, and gives you a book with the review.

```
/git pr-review 42
```
Reviews PR #42. Book title includes repo name and PR number.

#### GitHub Projects/Kanban
```
/git kanban-list
```
Lists all GitHub project boards for the current repository

```
/git kanban-view 1
```
Views project board #1 in a book. Book title includes repo name.

### Jira Commands

#### Create a Bug
```
/jira-create Bug Player cannot login after update
```
Creates a new Bug issue with the summary "Player cannot login after update"

#### Create with Description
```
/jira-create Story Add multiplayer support | Implement server-side logic for multiple players
```
Creates a Story with a description (use `|` separator)

#### View Issue
```
/jira-view PROJ-123
```
Fetches issue PROJ-123 and displays it in a book

#### List Issues
```
/jira-list mine    # Show only your assigned issues
/jira-list bugs    # Show all open bugs
/jira-list all     # Show all issues in the project
/jira-list         # Show all open issues (default)
```

### AI Assistant

#### Chat with AI
```
/ai-chat How do I implement a binary search tree in Java?
```
Short responses appear in chat, long responses come as a book.

#### Explain Code
```
/code-explain src/main/java/Main.java
```
Fetches the file from your repo and gets an AI explanation.

## Permissions

- `devcommands.*` - All commands (default: op)
- `devcommands.review` - Review PRs
- `devcommands.list` - List PRs
- `devcommands.ai` - AI chat
- `devcommands.explain` - Code explanation

## Configuration Options

### Settings
- `debug` - Enable debug logging
- `max-book-pages` - Maximum pages per book (default: 50, max: 100)
- `cache-duration` - Cache PR data in seconds
- `max-concurrent-requests` - Max simultaneous API requests
- `command-cooldown` - Seconds between commands per player

### Custom Prompts
Edit the `prompts` section in config.yml to customize AI behavior:

```yaml
prompts:
  pr-review: |
    Custom prompt for PR reviews...
    Use {pr_data} placeholder

  code-explain: |
    Custom prompt for code explanation...
    Use {code} placeholder
```

## Troubleshooting

### "Failed to fetch PR"
- Check your GitHub token is valid and has correct permissions
- Verify the repository name is correct (owner/repo format)
- Ensure the PR number exists

### "vLLM request failed"
- Check vLLM service is running: `oc get pods`
- Verify the service URL is correct
- Check vLLM logs: `oc logs deployment/vllm`

### Books not appearing
- Check inventory is not full
- Look in server logs for errors
- Verify permissions

### "Please wait X seconds"
- Cooldown between commands (configurable)
- Prevents API spam

## Requirements

- Paper/Spigot 1.20.4+ (or compatible version)
- Java 17+
- Maven (for building)
- GitHub personal access token
- vLLM instance (or compatible OpenAI API endpoint)

## Development

Build:
```bash
mvn clean package
```

Test locally:
```bash
# Copy to local test server
cp target/*.jar ~/minecraft-server/plugins/
```

## License

MIT

## Contributing

PRs welcome! Test your changes thoroughly before submitting.
