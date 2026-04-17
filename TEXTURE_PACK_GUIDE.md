# Minecraft Texture Pack Management Guide

## Current Setup

**Texture Pack:** F8thful (6.0 MB)
**SHA-1:** `59b8c545c7e6d6ae6a55aef81a62acacf82fb32e`
**URL:** `http://soyr-ws.sytes.net:32673/texture-pack.zip`
**Storage:** Persistent volume at `/mnt/texture-pack` on OKD node (192.168.122.10)

## Architecture

- **Texture Pack Server:** Python HTTP server running in OKD cluster (minecraft namespace)
- **Persistent Storage:** HostPath volume on OKD node
- **Network:** NodePort 32673 (forwarded through iptables on workstation)

## How to Update the Texture Pack

### 1. Upload new texture pack to workstation
```bash
# From your local machine
scp -i ~/.ssh/okd_workstation -P 49153 /path/to/new-pack.zip soyr@soyr-ws.sytes.net:~/Downloads/
```

### 2. Calculate SHA-1 hash
```bash
ssh -i ~/.ssh/okd_workstation -p 49153 soyr@soyr-ws.sytes.net "sha1sum ~/Downloads/new-pack.zip"
```

### 3. Copy to OKD node
```bash
ssh -i ~/.ssh/okd_workstation -p 49153 soyr@soyr-ws.sytes.net "scp ~/Downloads/new-pack.zip core@192.168.122.10:/tmp/"

ssh -i ~/.ssh/okd_workstation -p 49153 soyr@soyr-ws.sytes.net "ssh core@192.168.122.10 'sudo mv /tmp/new-pack.zip /mnt/texture-pack/texture-pack.zip && sudo chown 1000740000:0 /mnt/texture-pack/texture-pack.zip && sudo chmod 644 /mnt/texture-pack/texture-pack.zip && sudo chcon -t container_file_t /mnt/texture-pack/texture-pack.zip'"
```

### 4. Update Minecraft server with new SHA-1
```bash
ssh -i ~/.ssh/okd_workstation -p 49153 soyr@soyr-ws.sytes.net "oc login -u kubeadmin -p 'PH5VG-MIHSy-5vmCw-szBrx' https://api.sno.okd.local:6443 --insecure-skip-tls-verify=true && oc set env deployment/minecraft-server -n minecraft RESOURCE_PACK_SHA1='NEW-SHA1-HERE'"
```

### 5. Verify
```bash
# Check server.properties
ssh -i ~/.ssh/okd_workstation -p 49153 soyr@soyr-ws.sytes.net "oc exec -n minecraft deployment/minecraft-server -- cat /data/server.properties | grep -i resource"

# Test HTTP access
curl -I http://soyr-ws.sytes.net:32673/texture-pack.zip
```

## Port Forwarding Configuration

The following iptables rules forward port 32673 from the workstation to the OKD node:

```bash
sudo iptables -t nat -A PREROUTING -i wlp8s0 -p tcp --dport 32673 -j DNAT --to 192.168.122.10:32673
sudo iptables -I FORWARD -o virbr0 -d 192.168.122.10 -j ACCEPT
sudo iptables -I FORWARD -i virbr0 -s 192.168.122.10 -j ACCEPT
```

**Router:** Port 32673 must also be forwarded to the workstation (10.0.0.164) for external access.

## Troubleshooting

### Texture pack not downloading for players
1. Verify port 32673 is forwarded on your router
2. Test external access: `curl -I http://soyr-ws.sytes.net:32673/texture-pack.zip`
3. Check Minecraft logs for errors

### Pod can't access texture pack after restart
```bash
# Fix permissions on OKD node
ssh -i ~/.ssh/okd_workstation -p 49153 soyr@soyr-ws.sytes.net "ssh core@192.168.122.10 'sudo chmod 755 /mnt/texture-pack && sudo chown 1000740000:0 /mnt/texture-pack/texture-pack.zip && sudo chcon -R -t container_file_t /mnt/texture-pack'"
```

### Check pod logs
```bash
ssh -i ~/.ssh/okd_workstation -p 49153 soyr@soyr-ws.sytes.net "oc logs -n minecraft -l app=texture-pack-server --tail=50"
```

## Making Texture Pack Required

To force players to use the texture pack:
```bash
ssh -i ~/.ssh/okd_workstation -p 49153 soyr@soyr-ws.sytes.net "oc set env deployment/minecraft-server -n minecraft REQUIRE_RESOURCE_PACK='TRUE'"
```

## Deployed Resources

- **PersistentVolume:** texture-pack-pv (1Gi)
- **PersistentVolumeClaim:** texture-pack-pvc (minecraft namespace)
- **Deployment:** texture-pack-server (minecraft namespace)
- **Service:** texture-pack-service (ClusterIP on port 80, NodePort 32673)
