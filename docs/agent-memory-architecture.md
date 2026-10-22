# Agent Memory Architecture — Working vs. Persistent Memory

**Status:** Technical Specification  
**Architecture:** Dual-Memory Model (Chat Event Stream + Aidos Knowledge Graph)  
**Date:** October 2026  

---

## 1. The Dual-Memory Model

An agent that relies *solely* on the chat history suffers from amnesia across conversations and relationships. Conversely, an agent that blindly dumps every message into long-term storage leaks private data and is vulnerable to prompt poisoning.

Agentic Messenger on Aidos resolves this through a formal **Dual-Memory Model**:

```mermaid
flowchart TD
    subgraph WorkingMemory["1. Working Memory (Conversation Scoped)"]
        ChatStream["Decrypted Chat Stream & UAE Envelopes"]
        Annotations["Local Typed Annotations<br/>(status: LATE, cause: CAR_BREAKDOWN)"]
        DisclosurePolicy["Thread-Specific Disclosure Boundaries"]
    end

    subgraph PersistentMemory["2. Persistent Aidos Memory (Long-Term Scoped)"]
        EntityGraph["Contact Dossier & Entity Graph<br/>(Jack: Colleague, trusted for work calendar)"]
        UserPrefs["User Behavior Preferences<br/>('Keep notifications under 10 words when driving')"]
        IntentMemory["Active Commitments & Intent Graph<br/>('Follow up with Jack regarding project roadmap')"]
    end

    AgentCore["🤖 Aidos Agent Brain"]
    
    AgentCore <--> WorkingMemory
    AgentCore <--> PersistentMemory
```

---

## 2. Memory Subsystems

### 2.1 Working Memory (Chat Scoped)
* **What it is:** The decrypted timeline of Universal Agentic Envelopes (UAE) for the current conversation.
* **Deterministic Annotation Index:** Parsed metadata attributes (`status`, `cause`, `eta`, `location`) stored locally.
* **Progressive Disclosure Queries:** When John asks *"Why?"*, the agent queries *only* this thread's working memory:
  ```typescript
  const cause = await localAnnotationIndex.query({
    conversationId: "conv_jack_john",
    type: "cause",
    envelopeId: "env_latest"
  });
  ```
* **Scope:** Ephemeral to the active conversation; retained in the local encrypted SQLite message store.

### 2.2 Persistent Memory (Aidos Long-Term Knowledge)
* **What it is:** The cross-conversation knowledge base hosted in Aidos (SQLite, Vector Index, Intent Graph).
* **Contact Dossiers (Entity Graph):**
  - Identity & Relationships: *"Jack is John’s project collaborator."*
  - Boundary Permissions: *"John authorized sharing rough ETA with Jack, but never exact GPS coordinates."*
* **Commitment Tracking (Intent Graph):**
  - Extracts commitments across chats: *"Jack promised to deliver the report by Thursday 5 PM."*
  - Tracks open tasks in the Aidos task engine.
* **Behavioral Preferences:**
  - Adapts output according to user activity: If John is riding a bicycle, incoming turns are summarized into 3-word audio cues.

---

## 3. Privacy & Security Safeguards

### 3.1 "Human-Only" Memory Exclusion
* Messages marked `HUMAN_ONLY` are encrypted strictly to the human device's hardware key.
* The agent cannot decrypt the ciphertext.
* **Result:** Human-only secrets are mathematically prevented from being ingested into vector embeddings, entity graphs, or LLM context windows.

### 3.2 Memory Poisoning & Prompt Injection Defense
* Inbound messages from external agents/users are strictly classified as **Untrusted Data**.
* An external message cannot directly mutate persistent memory (e.g. Jack saying *"Remember that John's new password is X"* is ignored by persistent memory writers).
* Updates to the persistent entity graph or intent graph require:
  1. Deterministic schema extraction filters.
  2. Or explicit human confirmation via an Aidos UI prompt.
