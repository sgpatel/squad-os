# TutorOS — self-hosted stack

One `docker compose up` brings up:

| Service       | Image                          | What it does |
|---------------|--------------------------------|--------------|
| `postgres`    | `pgvector/pgvector:pg16`       | Episodic memory (pgvector) + mastery graph (M3-JDBC) |
| `ollama`      | `ollama/ollama:latest`         | Local LLM + embedder, no external API key needed |
| `ollama-init` | `alpine:3.20`                  | One-shot pull of chat + embed models on first boot |
| `tutor-api`   | built from `tutor-api/`        | Spring Boot, `selfhost` profile |
| `tutor-ui`    | built from `tutor-ui/`         | React build behind nginx, proxies to api |

Memory and mastery survive container restarts. No `OPENAI_API_KEY` required.

## Quick start

From `examples/tutor-os/`:

```bash
cp .env.example .env       # tweak ports / model choice if you want
docker compose up -d
docker compose logs -f tutor-api  # watch the boot
```

First boot pulls ~5 GB of models into the `ollama-models` volume —
`docker compose logs -f ollama-init` shows progress. Subsequent boots
reuse the cached models.

Once `tutor-api` reports healthy, open <http://localhost:5173>.

## What you should see

- `[TutorOS] MasteryGraphStore: jdbc (persistent)` in the api logs
- `[TutorOS] MemoryRouter ready (working=in-process, episodic=pgvector, semantic=in-process)`
- The first chat turn pulls a chat completion from Ollama and writes
  a row to `squad_memories` (pgvector) and another to `tutor_mastery`
  (JDBC). Verify with:

```bash
docker compose exec postgres psql -U tutoros -d tutoros -c \
  "SELECT count(*) FROM squad_memories;  SELECT count(*) FROM tutor_mastery;"
```

## Switching to OpenAI (cloud mode)

Set `OPENAI_API_KEY` in `.env`. The selfhost profile clears it by
default; setting it both:

1. Lets Spring AI's OpenAI auto-config wire `OpenAiChatModel` and
   `OpenAiEmbeddingModel`.
2. Keeps everything else (pgvector memory, Postgres mastery) in
   place — only the LLM provider flips.

You'll typically also want to bump `EMBED_DIMS=1536` for OpenAI's
`text-embedding-3-small`. Existing pgvector rows written with the
768-dim Ollama embedder won't be readable after switching — wipe
the volume (`docker compose down -v`) when changing dimensions.

## GPU acceleration

The `ollama` service is CPU-only by default — `llama3.2:3b` runs at
~5–10 tok/s on a modern laptop, which is fine for a single learner
but slow for a classroom. To enable an NVIDIA GPU:

1. Install [`nvidia-container-toolkit`](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/install-guide.html) on the host.
2. Uncomment the `deploy.resources.reservations.devices` block in
   `docker-compose.yml` under the `ollama` service.
3. `docker compose up -d ollama` to recreate.

## Resetting state

```bash
docker compose down -v   # nukes both volumes — fresh DB, fresh model cache
```

## Layout

```
examples/tutor-os/
├── docker-compose.yml
├── .env.example
├── tutor-api/
│   └── Dockerfile                             # multi-stage Maven → JRE 21
├── tutor-ui/
│   ├── Dockerfile                             # multi-stage Vite → nginx
│   └── nginx.conf                             # SPA fallback + /api proxy
└── docker/
    ├── README.md                              # this file
    ├── init-db.sql                            # CREATE EXTENSION vector
    └── ollama-init.sh                         # ollama pull on first boot
```

## Troubleshooting

- **`ollama-init` hangs pulling**: model server saturated or slow
  network. The container restarts on failure — re-run with
  `docker compose up -d --force-recreate ollama-init`.
- **`tutor-api` boots but every chat 500s**: check `Ollama` is healthy
  and the model name in `.env` matches what `ollama list` reports
  inside the `ollama` container.
- **Memory recall returns nothing**: the pgvector schema is created
  on first write. Send a chat turn through, then check
  `SELECT count(*) FROM squad_memories;`.
- **`docker compose down -v` warning about external volumes**:
  expected — Compose names the volumes `tutoros_postgres-data` and
  `tutoros_ollama-models`, not `external`. The `-v` flag drops them
  with the stack.
