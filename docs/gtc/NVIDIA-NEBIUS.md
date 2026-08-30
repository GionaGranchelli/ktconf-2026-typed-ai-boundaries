# NVIDIA + Nebius deployment notes

Last research refresh: **2026-08-30**.

This file is an implementation aid, not a substitute for checking current vendor documentation at execution time.

## 1. NVIDIA hosted API — GLOBAL_CLOUD

Build.NVIDIA.com exposes OpenAI-compatible hosted inference at:

```text
https://integrate.api.nvidia.com/v1
```

Initial contest model:

```text
nvidia/nemotron-3.5-lightning-30b-a3b
```

Current NVIDIA example shape:

```python
from openai import OpenAI

client = OpenAI(
    base_url="https://integrate.api.nvidia.com/v1",
    api_key=os.environ["NVIDIA_API_KEY"],
)

completion = client.chat.completions.create(
    model="nvidia/nemotron-3.5-lightning-30b-a3b",
    messages=[{"role": "user", "content": "..."}],
)
```

The current KTConf app already contains an OpenAI-compatible provider adapter, so the preferred first implementation is configuration/composition rather than a new HTTP client.

Official model page:

- https://build.nvidia.com/nvidia/nemotron-3.5-lightning-30b-a3b

## 2. NVIDIA NIM container — EU_CLOUD

NVIDIA currently documents the Nemotron 3.5 Lightning 30B A3B NIM self-host path as:

```text
nvcr.io/nim/nvidia/nemotron-3.5-lightning-30b-a3b:latest
```

Current basic local container shape from NVIDIA:

```bash
export LOCAL_NIM_CACHE=~/.cache/nim
mkdir -p "$LOCAL_NIM_CACHE"

docker run -it --rm \
  --gpus all \
  --shm-size=16GB \
  -v "$LOCAL_NIM_CACHE:/opt/nim/.cache" \
  -p 8000:8000 \
  nvcr.io/nim/nvidia/nemotron-3.5-lightning-30b-a3b:latest
```

Before contest freeze:

- replace `latest` with a tested tag/digest;
- confirm NIM's actual model ID via `/v1/models`;
- confirm whether model artifacts need `NGC_API_KEY` at runtime;
- confirm persistent cache requirements in Nebius;
- record startup logs proving the image/model used.

Official sources:

- https://build.nvidia.com/nvidia/nemotron-3.5-lightning-30b-a3b?nim=self-hosted
- https://docs.nvidia.com/nim/large-language-models/latest/get-started/installation.html

## 3. NGC registry authentication

Current NVIDIA authentication command when credentials are required:

```bash
echo "$NGC_API_KEY" | docker login nvcr.io \
  --username '$oauthtoken' \
  --password-stdin
```

Important:

- username is the literal `$oauthtoken`;
- password is the NGC Personal API key;
- current NVIDIA docs say many public NIM images support keyless pull, but model artifacts or selected images can still require credentials;
- generate/use a Personal API key with at least NGC Catalog scope when needed.

For the Nebius deployment, do not expose the NGC key in shell history if avoidable. Store registry credentials in Nebius MysteryBox and pass them using the Serverless AI `--registry-secret` mechanism.

## 4. Nebius region choice

Preferred region:

```text
eu-west1 = France
```

Nebius currently documents public EU regions including:

```text
eu-north1 = Finland
eu-west1  = France
```

`eu-west1` currently supports NVIDIA H200.

Official region source:

- https://docs.nebius.com/overview/regions

## 5. Nebius GPU choice

Preferred contest EU GPU:

```text
platform: gpu-h200-sxm
preset:   1gpu-16vcpu-200gb
```

Nebius currently documents NVIDIA H200 as:

- Hopper architecture;
- 141 GB HBM3e;
- available in `eu-west1` France;
- single-GPU preset available.

Official source:

- https://docs.nebius.com/compute/virtual-machines/types

This is intentionally stronger contest branding than using a generic hyperscaler GPU instance: the EU route becomes Nebius + NVIDIA H200 + NVIDIA NIM + NVIDIA Nemotron.

## 6. Nebius Serverless AI endpoint is preferred

Nebius Serverless AI endpoint creation supports:

- arbitrary container image references;
- NVIDIA/private registry credentials;
- `--registry-secret`;
- NVIDIA GPU platform selection;
- public endpoint exposure;
- endpoint token authentication;
- environment variables/secrets;
- configurable disk and shared memory.

Official CLI reference:

- https://docs.nebius.com/cli/reference/ai/endpoint/create
- https://docs.nebius.com/serverless/endpoints/manage

Generic deployment shape:

```bash
nebius ai endpoint create \
  --name gtc-eu-nvidia-nim \
  --image nvcr.io/nim/nvidia/nemotron-3.5-lightning-30b-a3b:<PINNED_TAG> \
  --registry-secret <NGC_REGISTRY_SECRET> \
  --platform gpu-h200-sxm \
  --preset 1gpu-16vcpu-200gb \
  --public \
  --container-port 8000 \
  --auth token \
  --token-secret <ENDPOINT_AUTH_SECRET> \
  --shm-size 16Gi
```

This is a **command shape**, not yet a tested project command. The implementation agent must validate:

- subnet requirements;
- disk/cache volume;
- NIM entrypoint behavior;
- runtime environment requirements;
- endpoint URL format;
- health/startup timing;
- selected image's registry requirements.

## 7. Nebius Container Registry — optional, not required

Nebius has a regional Container Registry:

```text
cr.<region>.nebius.cloud/<registry-path>
```

Example for France:

```text
cr.eu-west1.nebius.cloud/<registry-path>
```

Basic Nebius flow:

```bash
nebius registry create --name gtc-nim-mirror
nebius registry configure-helper
```

Then tag/push an image into the resulting registry path.

Official sources:

- https://docs.nebius.com/container-registry
- https://docs.nebius.com/container-registry/quickstart

### When to use it

Mirror the NIM image only if we need one of:

1. reliability against upstream-registry availability;
2. a locally controlled immutable image copy;
3. simpler same-project deployment authentication;
4. a workaround because direct `nvcr.io` pull is incompatible with the selected Nebius path.

### When not to use it

Do not create/mirror images merely because the endpoint is on Nebius. Nebius Serverless AI explicitly supports private-registry credentials, including NVIDIA registries.

## 8. Discontinued Nebius NIM standalone app

Nebius documentation currently states its old **NVIDIA NIM microservices Standalone Applications were discontinued on 2026-05-18**.

Do not waste time trying to find that old one-click flow.

Current source:

- https://docs.nebius.com/applications/standalone/nvidia-nim

Use Serverless AI endpoint or Compute VM instead.

## 9. Raw VM fallback

If Serverless AI prevents NIM from starting correctly, fall back to a Nebius Compute VM using the same region/GPU.

Target:

```text
region:   eu-west1
platform: gpu-h200-sxm
preset:   1gpu-16vcpu-200gb
```

Then:

1. install/verify Docker + NVIDIA runtime;
2. authenticate to NGC if required;
3. pull the pinned NIM image;
4. run NIM on port 8000;
5. expose it through a controlled endpoint/network path;
6. keep endpoint authentication in front of it.

The contest architectural claim remains the same because the trust boundary is the region/provider/GPU deployment, not the Nebius product SKU used to host the container.

## 10. LOCAL model — NVIDIA RTX

Preferred first local model:

```text
nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF
```

Recommended first quantization:

```text
Q4_K_M
```

Official NVIDIA/Hugging Face GGUF supports llama.cpp. Starting shape:

```bash
llama serve -hf nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF:Q4_K_M \
  --host 0.0.0.0 \
  --port 8088
```

Sources:

- https://huggingface.co/nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF
- https://huggingface.co/blog/nvidia/nemotron-3-nano-4b

The local route should record NVIDIA GPU/CUDA evidence so the video/README can truthfully say the restricted workload stays on local NVIDIA RTX compute.

## 11. Optional higher-end local experiment

If desired after P0 is green, the user's multi-RTX system may be able to run a larger quantized Nemotron 3 Nano 30B A3B model across GPUs.

Current GGUF evidence exists for:

```text
ggml-org/NVIDIA-Nemotron-3-Nano-30B-A3B-GGUF
```

with a Q4_K_M file around 22.4 GB.

This is **P2**, not required. The 4B model is a cleaner LOCAL proof because the contest is demonstrating authority boundaries, not maximizing local benchmark quality.

## 12. Nebius + NVIDIA strategic relevance

Nebius is not being selected merely because it has a European data center.

As of 2026:

- NVIDIA and Nebius announced a deep strategic partnership across AI factories, inference and agentic AI;
- NVIDIA announced a $2B investment in Nebius;
- Nebius describes itself as an NVIDIA Reference/Exemplar Cloud partner and operates NVIDIA H200/Blackwell infrastructure;
- Nebius provides public EU regions with NVIDIA GPU capacity.

This makes the EU route a coherent NVIDIA-partner story for the contest.

Sources:

- https://nebius.com/newsroom/nvidia-and-nebius-partner-to-scale-full-stack-ai-cloud
- https://nebius.com/partner-catalog/nvidia
- https://nebius.com/compute

## 13. Secrets plan

Expected local environment names (names can change, values never enter git):

```text
GTC_NVIDIA_GLOBAL_API_KEY
GTC_NVIDIA_GLOBAL_BASE_URL
GTC_NVIDIA_GLOBAL_MODEL

GTC_NVIDIA_EU_BASE_URL
GTC_NVIDIA_EU_API_KEY
GTC_NVIDIA_EU_MODEL

GTC_NVIDIA_LOCAL_BASE_URL
GTC_NVIDIA_LOCAL_MODEL
GTC_NVIDIA_LOCAL_API_KEY   # optional/dummy only if adapter requires it
```

Cloud-side Nebius secrets:

```text
NGC registry credentials
Nebius endpoint bearer token
```

No screenshot, video, log or evidence pack may expose them.

## 14. First infrastructure spike checklist

Before modifying application semantics, prove these independently:

### GLOBAL

- [ ] `curl` to NVIDIA hosted Nemotron succeeds with the user's Build.NVIDIA.com key.
- [ ] model ID is recorded.

### LOCAL

- [ ] llama.cpp serves official NVIDIA Nemotron 3 Nano 4B GGUF on RTX.
- [ ] OpenAI-compatible chat request succeeds.

### EU

- [ ] Nebius `eu-west1` project exists/access works.
- [ ] H200 quota/capacity exists or quota request is filed.
- [ ] NIM image can be pulled or referenced with registry secret.
- [ ] NIM starts on one H200.
- [ ] Nebius endpoint returns an OpenAI-compatible completion.

Only after all three endpoints independently answer should the application integration be considered complete.
