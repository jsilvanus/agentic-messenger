# Agentic Messenger Platform (AMP)

An open, cryptographically secure messaging platform and protocol designed for a world where humans and AI agents collaborate as first-class communicators.

## 🏛️ Trusted Component Registry (TCR) & Component Domain Ownership (D46)

TCR stands for **Trusted Component Registry**. These pre-vetted, trusted components are used when generating the end goal: the **Desktop Generative UI** (in Aidos Desktop). While other projects are not Generative UI hosts, they author, create, and own their domain-specific UI components.

It is likely that the TCR will at some point move into a specific separate repository, but for now, all UI components are owned by the apps that first came up with them:
- **Agentic Messenger (`agentic-messenger` / AMP)**: Authoritative owner of **4-Party Conversation Mesh Protocols**, **Message Timelines** (`MessageBubble`, `ContactRow`, `DualViewToggle`, `CueTickerRow`), **Universal Envelopes (UAE)**, **Progressive Disclosure Approvals** (`ApprovalCard`, `CalendarCopilotCard`), and **Cryptographic Provenance Badges** (`ProvenanceBadge`, `PrivacySealBadge`).
- **Dictator (`dictator`)**: Authoritative owner of **Text Editing & Document Editing Interfaces** (`DocumentInputSection`), **Continuous Document Flows**, the **Red Pill (Ember Caret Actor)** speech-to-text cursor (`PersistentRedPill`, `FloatingActorPill`), and **TCR Staging Overlays** (`PillConstellationOverlay`, `CadenceEngine`, `HappeningsHUD`, `TranscriptSplitDrawer`).
- **Aidos (`aidos`)**: Authoritative owner of the **Desktop Generative UI Host Canvas**, **Core Agent Loop**, **Model Adapters**, **Tool Contracts**, and **Taint-Safe Execution**.

### Cross-Repository Consumption:
When Agentic Messenger (or AMP) uses **document editing** or **Red Pill tools**, it comes and fetches those components directly from **Dictator** rather than re-implementing them. Similarly, Aidos Desktop Generative UI imports AMP's conversation mesh and approval cards.

See the canonical specification: [Aidos TCR Component Domain Owners (docs/TCR_COMPONENT_DOMAIN_OWNERS.md)](../aidos/docs/TCR_COMPONENT_DOMAIN_OWNERS.md).

## Subprojects

- **`:amp-core`**: Universal Agentic Envelope (UAE) data models, polymorphic serialization (`kotlinx.serialization`), and blinded mailbox token generation (`HMAC-SHA256`).
- **`:amp-server`**: Zero-Knowledge Blind Relay Server (Ktor + Netty + SQLite):
  - **Blinded Mailbox Router**: Microsecond in-memory routing to active WebSocket sessions matching blinded tokens (`mailbox_id = HMAC(epoch, device_id)`).
  - **Ephemeral Store-and-Forward Queue**: SQLite-backed queue with configurable TTL (default 14 days) and immediate, permanent deletion upon cryptographic delivery ACK.
  - **Client-Encrypted Blob Storage**: Zero-knowledge ciphertext storage for voice dictation audio (Red Pill) and media attachments with SHA-256 integrity verification.
  - **Public KeyPackage Registry**: Zero-knowledge directory for MLS pre-keys and device credentials indexed by hashed identity commitments.

## Building and Testing

```bash
# Run tests across all modules
./gradlew test

# Run the blind relay server
./gradlew :amp-server:run
```

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `RELAY_PORT` | `8080` | Port for the HTTP and WebSocket server |
| `RELAY_HOST` | `0.0.0.0` | Listen interface |
| `RELAY_DB_PATH` | `blind_relay.db` | SQLite database path for queue & key registry |
| `STORAGE_BACKEND` | `filesystem` | Storage backend: `filesystem` or `s3` |
| `RELAY_BLOB_DIR` | `data/blobs` | Directory for filesystem blob backend |
| `S3_ENDPOINT` | `http://localhost:7070` | S3 API endpoint (e.g. VersityGW, AWS, MinIO) |
| `S3_BUCKET` | `amp-blobs` | S3 bucket name |
| `S3_REGION` | `us-east-1` | S3 region |
| `S3_ACCESS_KEY` | `myaccesskey` | S3 access key |
| `S3_SECRET_KEY` | `mysecretkey` | S3 secret key |
| `S3_FORCE_PATH_STYLE`| `true` | Enable path-style S3 URLs (required for VersityGW/MinIO) |

## Docker Compose Stack (with VersityGW S3)

To spin up the Blind Relay together with a local **VersityGW** S3 gateway:

```bash
docker compose up -d
```

- **Relay Server**: `http://localhost:8080` (WebSocket at `/ws/relay`, Blobs at `/blobs`)
- **VersityGW S3 API**: `http://localhost:7070`
- **VersityGW Admin/WebUI**: `http://localhost:7071`


---

## 🏛️ UI Component Ownership & Design Vocabulary (D46)

In the cross-repository ecosystem, specific repositories maintain authoritative ownership over UI domains:
- **Dictator (`dictator`)**: Authoritative owner of **Document Editing Interfaces**, **Continuous Living Document Flows**, the **Red Pill (Ember Caret Actor)** speech-to-text cursor, and **TCR Staging Overlays** (`PillConstellationOverlay`, `FloatingActorPill`, `DocumentInputSection`, `TranscriptSplitDrawer`). In this model, text flows directly from the cursor and enters the document at the active caret.
- **Agentic Messenger (`agentic-messenger`)**: Authoritative owner of **4-Party Conversation Mesh Protocols**, **Universal Envelopes (UAE)**, **Blinded Relay Networking**, and **Cryptographic Provenance Badges** (Human Direct vs Delegated Runner vs Sealed E2EE).
- **Aidos (`aidos`)**: Authoritative owner of the **Core Agent Loop**, **Model Adapters**, **Tool Contracts**, and **Taint-Safe Execution**.

AMP adopts Dictator's TCR Document Model (`DocumentChatFlow`, `DocumentInputSection`, and draggable Red Pill cursor) to ensure cross-ecosystem consistency.
