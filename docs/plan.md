# Agentic Messenger — Implementation Plan & Architecture Specification

**Status:** Implementation Blueprint (Kotlin Multiplatform + High-Efficiency Relay)  
**Repository:** `jsilvanus/agentic-messenger` (`d:\agentic-messenger`)  
**Target:** Standalone Agentic Messaging Platform & Protocol (Native Aidos & Dictator Integration)  
**Date:** October 2026  

---

## 1. Executive Summary & Vision

**Agentic Messenger (AMP)** is an open, cryptographically secure messaging platform and protocol designed for a world where humans and AI agents collaborate as first-class communicators.

Unlike conventional chat applications (Signal, WhatsApp, Slack, Matrix) where AI exists only as external chatbots or automated webhooks, Agentic Messenger establishes:
* **First-Class Agent Participation:** Humans and AI agents possess distinct cryptographic identities, permissions, and delegation scopes.
* **All Four Communication Topologies:** Human-to-Human (H2H), Human-to-Agent (H2A), Agent-to-Human (A2H), and Agent-to-Agent (A2A).
* **Dual Interface:** A rich, responsive **Compose Multiplatform UI** alongside a native **Model Context Protocol (MCP) Interface** for AI agents.
* **Progressive Disclosure:** Agents can answer follow-up questions from already-shared information within strict, declared policy boundaries without pinging the sender unnecessarily.
* **Multi-Recipient E2EE (MLS RFC 9420):** Genuine end-to-end encryption with separate keys for humans and agent devices, supporting sealed "Human-Only" payloads that AI agents cannot decrypt.
* **Native Ecosystem Integration:** Direct Gradle in-process integration with **Aidos** (`kernel`, `engine`, `agent`, `sdk`) and **Dictator** (`dictator-kotlin`), sharing Kotlin Multiplatform models and memory with zero IPC overhead.

---

## 2. Technical Stack Selection & Domain Ownership

Following the architectural assessment of the Aidos and Dictator ecosystem:

* **Core Protocol & Client SDK:** **Kotlin Multiplatform (KMP)**
  - Direct dependency inside Aidos and Dictator (`implementation(project(":amp-core"))`).
  - Shared business logic, serialization (`kotlinx.serialization`), and coroutine reactive pipelines (`kotlinx.coroutines`, `Flow`).
  - Local Working Memory powered by **SQLDelight** (compiled type-safe SQLite running on Android, Desktop, and iOS).
* **User Interface:** **Compose Multiplatform (Jetpack Compose) — Domain Ownership: `agentic-messenger` (`amp-ui`)**
  - **Authoritative Domain Owner:** `agentic-messenger` strictly owns this domain of multiplatform UI components (`amp-ui`), covering:
    - Login & Identity Setup: `LoginScreen`, `IdentityCard`, live HMAC blinded mailbox generation.
    - Contacts Directory & Mesh Presence: `ContactsScreen`, `ContactRow`, `PresenceIndicator` (with animated pulse waves).
    - 4-Party Conversation Mesh: `ConversationScreen`, `MessageBubble`, `ProvenanceBadge` (`👤 Human Direct`, `🤖 Delegated Runner`, `🔒 Human-Only E2EE`, `⚙️ System Service`).
    - Dual-View Toggle: `DualViewToggle` and `CueTickerRow` (Natural Language Stream vs Condensed Cue-Words Ticker).
    - Progressive Disclosure & Smart Actions: 1-tap `ApprovalCard` for topic escalations, inline `CalendarCopilotCard`.
    - Generative UI Registry: `GenerativeUiRegistry` dispatching Universal Agentic Envelopes (UAE) to interactive Compose cards.
  - **Consumption by Aidos Desktop & Mobile:** Aidos Desktop directly consumes these components as atomic and molecular Generative UI blocks for spatial canvas nodes, chat panes, and execution timelines without duplicating UI code.
  - **Boundary with Dictator:** Voice capture and Red Pill UI planning are owned by `dictator`; `agentic-messenger` only provides the audio dictation playback waveform component (`WaveformVisualizer`) and composer trigger slot.
  - Native Android APK (hardware-accelerated, native feel).
  - Native Desktop app (Windows, macOS, Linux).
  - Web fallback via Compose for Web / Kotlin/Wasm.
* **Blind Relay Server:** **Ultra-High Efficiency Blind Relay (Rust / Tokio or High-Performance Ktor / Netty)**
  - Zero-knowledge blinded mailbox routing (`mailbox_id`).
  - Extremely low CPU and memory footprint (< 15 MB RAM at idle; microsecond routing latency).
  - High concurrency WebSocket connections without GC stutter.
  - Ephemeral store-and-forward queue with strict TTL and delivery ACKs.
* **Agent Integration:** **Model Context Protocol (MCP)**
  - Standalone MCP server exposing deterministic tools (`send_message`, `query_conversation`, `answer_from_context`, `request_progressive_disclosure`).
  - Compatible with Claude, Cursor, local Aidos runners, and Ollama.

---

## 3. Project Structure & Module Boundaries

```text
agentic-messenger/
├── amp-core/                     # KMP: Universal Agentic Envelope, Serialization & IBP Policy Engine
│   └── src/commonMain/kotlin/    # Zod-like type validation, Provenance, Boundary Evaluator
├── amp-crypto/                   # KMP: MLS RFC 9420, Double Ratchet, Hardware Enclaves
│   ├── src/commonMain/kotlin/    # Cryptographic interfaces & envelope encryption
│   ├── src/androidMain/kotlin/   # Android StrongBox / KeyStore hardware integration
│   └── src/jvmMain/kotlin/       # Desktop OS Keychain / Libsodium bindings
├── amp-client-sdk/               # KMP: Connection manager, SQLDelight Working Memory, outbox queue
├── amp-server/                   # High-Efficiency Blind Relay (Rust / Tokio or Ktor Netty)
├── amp-mcp/                      # JVM/KMP: Model Context Protocol adapter for AI agents
├── amp-ui/                       # Compose Multiplatform UI: Android, Desktop & Web (Owns UI domain)
└── docs/                         # Specifications & Architectural Blueprints
    ├── plan.md                   # This master blueprint
    ├── roadmap.md                # 6-phase engineering milestones
    ├── aidos-joint-app-ux.md     # 4-party conversation mesh & Jack/John flows
    ├── identity-and-cryptography.md # Multi-device key hierarchy & MLS E2EE
    ├── relay-and-storage.md      # Blind router, store-and-forward queue & invites
    ├── agent-memory-architecture.md # Dual-memory model: Working vs Persistent
    ├── brainstorm-chatgpt.md     # Full ChatGPT brainstorm transcript
    └── brainstorm-turns-12-to-15.md # Brainstorm technical turning points
```

---

## 4. Message & Protocol Specification

### 4.1 Universal Agentic Envelope (UAE) Data Model

```kotlin
@Serializable
data class UniversalEnvelope(
    val envelopeId: String,
    val conversationId: String,
    val epoch: Long,
    val timestamp: Instant,
    val routing: RoutingHeader,
    val provenance: ProvenanceRecord,
    val policy: InformationBoundaryPolicy,
    val payload: EnvelopePayload
)

@Serializable
data class RoutingHeader(
    val sender: EndpointIdentifier,
    val recipients: List<RecipientTarget>
)

@Serializable
data class EndpointIdentifier(
    val principalId: String,        // e.g. "did:aidos:jack"
    val endpointType: EndpointType, // HUMAN_DIRECT, AGENT_DELEGATE, SYSTEM_SERVICE
    val deviceKeyId: String         // e.g. "key_jack_mobile_01"
)

@Serializable
data class InformationBoundaryPolicy(
    val privacyLevel: PrivacyLevel, // AGENT_ACCESSIBLE, HUMAN_ONLY
    val progressiveDisclosure: ProgressiveDisclosureRules? = null
)

@Serializable
data class ProgressiveDisclosureRules(
    val allowDirectInference: Boolean = true,
    val allowedTopics: List<String> = emptyList(),    // e.g. ["DELAY_REASON", "VEHICLE_ISSUE"]
    val escrowedTopics: List<String> = emptyList()    // e.g. ["ESTIMATED_ARRIVAL_TIME", "LIVE_GPS"]
)

@Serializable
data class EnvelopePayload(
    val naturalLanguage: String,
    val structuredAnnotations: List<TypedAnnotation> = emptyList()
)

@Serializable
data class TypedAnnotation(
    val type: String,               // "status", "cause", "eta", "location"
    val confidence: Double,
    val attributes: Map<String, String>
)
```

---

## 5. Security & Threat Model

1. **Deterministic Information Boundary Enforcement:**
   - LLMs are **never** permitted to decide privacy policies alone.
   - The `amp-core` policy evaluator determines whether a follow-up matches `allowedTopics` or `escrowedTopics` via formal constraint validation before any prompt reaches an LLM.
2. **Dual-Compartment Cryptographic Isolation:**
   - When marked `HUMAN_ONLY`, symmetric content keys are wrapped solely for human device public keys (`human_device_key`).
   - The agent leaf key receives a cryptographically sealed tombstone. Agents physically cannot decrypt the message.
3. **Auditability & Provenance:**
   - Every turn retains its cryptographic Ed25519 signature and assertion level (`ORIGINAL_HUMAN` vs. `AGENT_SYNTHESIZED`).
