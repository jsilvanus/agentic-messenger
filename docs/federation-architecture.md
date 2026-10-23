# Federated Blind Relay Architecture (Future Work)

**Status:** Architecture Blueprint & RFC  
**Target:** Multi-Domain Server-to-Server (S2S) Federation  
**Security Level:** Zero-Knowledge, Mutual TLS (mTLS), Untrusted Intermediaries  
**Date:** October 2026  

---

## 1. Vision & Architectural Goals

In conventional federated networks (such as Matrix, XMPP, or ActivityPub), federating servers inspect structured message schemas, group states, membership lists, and metadata. This leaks relational metadata and requires complex state synchronisation engines.

**Agentic Messenger Platform (AMP)** federation operates under an entirely different paradigm:
* **Zero-Knowledge Federation**: Federating servers act solely as **untrusted blind mail routers**. They never inspect plaintext bodies, agent reasoning transcripts, annotations, or cryptographic identity keys.
* **DID-Based Addressing**: Participants and agents are addressed via Decentralized Identifiers (DIDs) (e.g., `did:web:company-a.com:alice`, `did:aidos:bob`).
* **Decoupled Ephemeral Routing**: Senders resolve the recipient's home relay domain and dispatch an opaque, blinded ciphertext envelope directly over an encrypted Server-to-Server (S2S) tunnel.

---

## 2. Federated Topologies & Data Flow

```mermaid
sequenceDiagram
    autonumber
    participant AliceDevice as 👤 Alice (Domain A)
    participant RelayA as 🛡️ Relay A (relay.domain-a.org)
    participant RelayB as 🛡️ Relay B (relay.domain-b.com)
    participant BobDevice as 👤/🤖 Bob / Bob's Agent (Domain B)

    AliceDevice->>AliceDevice: 1. Encrypt UAE Envelope with Bob's MLS Leaf Key
    AliceDevice->>RelayA: 2. WebSocket SEND(dest: "mb_bob@relay.domain-b.com", ciphertext)
    RelayA->>RelayB: 3. S2S mTLS Forward POST /federation/v1/inbound (blinded token, ciphertext)
    RelayB->>RelayB: 4. Check active sessions for mb_bob; queue in Store-and-Forward if offline
    RelayB->>BobDevice: 5. WebSocket DELIVER(ciphertext)
    BobDevice->>RelayB: 6. WebSocket ACK(messageId)
    RelayB->>RelayA: 7. S2S Delivery ACK Notification
    RelayA->>AliceDevice: 8. WebSocket SENT / DELIVERED confirmation
```

---

## 3. Core Federated Subsystems

### 3.1 Domain-Scoped Blind Mailbox Tokens
Mailbox routing tokens in a federated environment carry domain routing qualifiers:
$$\text{routing\_target} = \text{mailbox\_id} @ \text{home\_domain}$$
* $\text{mailbox\_id} = \text{HMAC-SHA256}(K_{\text{epoch}}, \text{device\_id})$: Identical to single-domain relay, maintaining zero-knowledge device unlinkability.
* $\text{home\_domain}$: Resolves via DNS SRV record `_amp-relay._tcp.<home_domain>` or `.well-known/amp-relay.json`.

### 3.2 Server-to-Server (S2S) Protocol
* **Transport**: Mutual TLS (mTLS) with standard TLS 1.3 and domain certificate verification.
* **Endpoints**:
  * `POST /federation/v1/envelopes`: Dispatches an opaque ciphertext envelope to Relay B.
  * `POST /federation/v1/ack`: Relays delivery receipts across domain boundaries to trigger queue cleanup.
  * `GET /keys/{identityCommitment}`: Public KeyPackage discovery (MLS pre-keys and device certs) hosted on Relay B.

### 3.3 Zero-Knowledge Federated Blob Storage (Voice Dictation & Attachments)
Federated blob transfers require **zero relay-to-relay mirroring**:
1. Alice encrypts audio bytes with local symmetric key $K_{\text{media}}$ (AES-256-GCM).
2. Alice uploads ciphertext to Relay A: `https://relay.domain-a.org/blobs/b_987abc`.
3. Alice includes the blob URL, SHA-256 hash, and $K_{\text{media}}$ inside the end-to-end encrypted envelope.
4. When Bob receives the envelope via Relay B, Bob's client fetches the ciphertext directly from Relay A via HTTPS `GET /blobs/b_987abc`.
5. Relay A serves the opaque ciphertext without knowing who is downloading it; Bob decrypts locally with $K_{\text{media}}$.

---

## 4. Trust & Threat Model

* **Compromised Intermediary Relay**: If Relay A or Relay B is compromised, attackers observe only:
  - Blinded tokens (`mb_...`)
  - Ciphertext envelope bytes
  - Opaque media blobs
  They cannot determine user handles, message contents, annotations, or communication intent.
* **Replay Protection**: Envelopes carry millisecond timestamps and unique UUIDv7 `envelopeId`s. S2S endpoints reject replayed frames outside a ±5-minute drift window.
* **Spam & Anti-Abuse**: Relay B can enforce rate-limiting per source domain and require cryptographic Proof-of-Work (PoW) or signed domain commitments before accepting inbound S2S traffic from unknown federations.
