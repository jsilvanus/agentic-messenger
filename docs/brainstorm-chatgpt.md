# Original AMP Brainstorm Session (ChatGPT)

> **Source URL:** https://chatgpt.com/share/6ada6e7a-76c8-83eb-b282-c8a3d1f7add0

You said:

I want make an agentic messaging app. It has human ui as well, but you can use it via mcp. U can send agent to human, agent to agent, human to agent, h2h messages. You could even require only human read the message (then app use required). 

ChatGPT said:

You’re describing something that’s part messaging platform, part agent protocol, part trust layer—and that combination is where things get tricky fast. The idea is solid, but a naive implementation will either turn into “just another chat app” or a security nightmare.

Let’s break what you’re really proposing into concrete pieces and where the hard problems are.

1. Core Concept (what’s actually new here)

You’re not just building chat—you’re defining a multi-actor messaging system:

Human → Human (H2H)
Human → Agent (H2A)
Agent → Human (A2H)
Agent → Agent (A2A)

And importantly:

Messages can carry execution intent
Messages can carry access constraints (e.g. “only a human can read this”)

That’s closer to a mix of:

Slack (UI + messaging)
Matrix (protocol thinking)
OpenAI MCP (agent interoperability)

But none of those fully solve what you’re aiming for.

2. The genuinely interesting idea: “Message Policies”

Your “only human can read this” is actually the strongest differentiator.

Think of messages having a policy envelope:

{
  "content": "...",
  "from": "agent:calendar-bot",
  "to": "user:juha",
  "policy": {
    "readable_by": ["human"],
    "forwardable": false,
    "executable": false
  }
}


Or:

{
  "content": "Book meeting at 15:00",
  "policy": {
    "executable_by": ["agent:calendar"],
    "requires_confirmation": true
  }
}


Now you’re not sending messages—you’re sending capabilities + constraints.

3. Identity model (this will make or break it)

You need first-class identity types:

human:<id>
agent:<id>
service:<id> (optional but useful)

Each identity needs:

authentication (OAuth, keys, etc.)
trust level
execution permissions

If you skip this, agent-to-agent becomes chaos.

4. MCP integration (your “agent transport layer”)

If you want MCP compatibility:

Treat MCP endpoints as agent adapters
Your app becomes a message broker + policy enforcer

Flow:

Agent → MCP → Your App → Policy Check → Target (Human UI / Agent)


Important: MCP itself doesn’t enforce intent safety—you must.

5. Execution vs Messaging (critical separation)

Do NOT blur these:

Messaging = “here is information”
Execution = “do something”

If an agent sends:

“Delete all files”

That should NOT auto-execute unless:

policy allows it
recipient agent is trusted
(likely) human confirms

Otherwise you’ve built a remote exploit framework 😄

6. “Human-only messages” — how to actually enforce?

This is harder than it sounds.

Options:

Weak enforcement (easy)
UI hides message from agents
backend flags it

Problem: any agent with API access can still read it.

Strong enforcement (interesting)
End-to-end encryption
Key only available in human UI (device-bound)

Now:

agents literally cannot decrypt

This is closer to:

Signal style security
7. Agent-to-Agent messaging (A2A)

This is where things get powerful:

Example:

travel-agent → calendar-agent → payment-agent


But you need:

rate limiting
cost tracking
permission scopes

Otherwise agents will:

spam each other
trigger loops
burn money
8. UI is still critical

Even if MCP is core, humans need:

inbox (with message types: info / request / action)
approval flows
audit logs (VERY important)

Think:

“Agent wants to send email on your behalf → Approve?”
9. Minimal viable architecture

If you want to actually build this, don’t over-engineer:

v1:

Node.js backend (fits your stack)
Postgres (you already use it)
WebSocket for realtime
Simple policy model:
readable_by: human | agent | both
requires_confirmation: boolean
One MCP adapter
10. What I’d push back on

Two risky ideas in your concept:

1. “Everything in one system”

This will explode in complexity.

Better:

messaging core
agent execution layer
policy engine

Keep them modular.

2. “Agents freely messaging agents”

Without strict controls, this becomes:

infinite loops
unintended automation chains

You need:

TTL for messages
execution depth limits
11. What this could become (if done right)

You’re basically heading toward:

a “permissioned agent network”
where messaging = API + UI + workflow

That’s actually quite novel if you nail:

policy enforcement
identity
execution safety
If you want next step

I can help you design:

exact DB schema
MCP adapter structure
message lifecycle (send → validate → deliver → execute)

Or we can sketch a real v1 API + example flows (e.g. “agent books meeting with human approval”).

You said:

It could also be: send message to John thst I'll be late. My car broke. 

Agent sends to john. John's agent gets notified (let's not think how) and it days: jack is late
John can ask: why?
Agent: John's car broke.
Jack: era?
Agent: he shared location. Eta 15 mins. 

ChatGPT said:

That flow makes sense conceptually—but as written, it quietly assumes something dangerous: that agents can freely reinterpret and relay private context. If you build it like that, you’ll leak information all over the place.

Let’s tighten your example into something that actually works.

Your scenario (cleaned up)
Human (Jack): “Send message to John that I’ll be late. My car broke.”
Jack’s agent:
Sends a message to John (or John’s agent)
John’s side:
Sees: “Jack is late”
Asks: “why?”
Gets: “Car broke”
Asks: “ETA?”
Gets: “15 minutes (based on shared location)”
The hidden problem: implicit data sharing

Right now your flow assumes:

The agent can extract meaning (“car broke”)
The agent can decide what to share
The agent can infer ETA from location
The agent can reveal that to John

That’s a lot of autonomous decisions, and each one needs permission.

Fix: split message into “facts” + “permissions”

Instead of one blob of text, Jack’s message becomes structured:

{
  "intent": "notify_delay",
  "target": "john",
  "data": {
    "status": "late",
    "reason": "car_broke",
    "location": {
      "shared": true,
      "precision": "approx"
    }
  },
  "policy": {
    "share_reason": "on_request",
    "share_location": "on_request",
    "derive_eta": true
  }
}


Now the agent isn’t guessing—it’s executing rules.

What John actually receives first

By default (minimal disclosure):

“Jack is running late.”

That’s it.

Then the conversation becomes progressive disclosure
Step 1

John: “Why?”

Agent checks:

share_reason = on_request ✅

Agent replies:

“His car broke down.”
Step 2

John: “ETA?”

Agent checks:

location shared? ✅
derive ETA allowed? ✅

Agent computes and replies:

“Estimated arrival in 15 minutes.”
Key concept: agents don’t “know”—they’re allowed

This is the mental shift:

❌ Agent “decides” what to say
✅ Agent checks what it’s allowed to say
Now the interesting part: Agent-to-Agent vs Agent-to-Human

You actually have two parallel conversations:

A2A (behind the scenes)

Jack’s agent → John’s agent:

{
  "type": "status_update",
  "subject": "jack",
  "data": {...},
  "policy": {...}
}

A2H (what John sees)

John’s agent translates that into:

human-readable summaries
answers to follow-up questions
Important edge case (this will bite you)

What if Jack did NOT allow reason sharing?

Then:

John: “Why?” Agent:

“Jack didn’t share the reason.”

That’s correct behavior, even if it feels slightly awkward.

Another edge: location sensitivity

Instead of raw GPS, define levels:

{
  "location": {
    "shared": true,
    "precision": "city" // or exact, route, eta_only
  }
}


So ETA can be shared without exposing location.

What makes your idea powerful

This pattern enables:

Asynchronous conversations via agents
Reduced cognitive load for humans
Privacy-aware automation

It’s basically:

“Agents negotiate context on behalf of humans, within strict rules.”
What I’d strongly recommend adding
1. “Ask before sharing” mode
"share_reason": "ask_owner"


Then flow becomes:

John asks “why?”
Jack gets notification:
“John is asking why you’re late. Share reason?”

Now you’ve got human-in-the-loop privacy control.

2. Message trace / audit

John should be able to see:

what came from Jack directly
what was inferred by the agent

Otherwise trust breaks.

3. Agent personality vs truth

Agents should NOT embellish:

Bad:

“Jack had an unfortunate car malfunction”

Good:

“Jack reported: car broke down”
Bottom line

Your example is good—but the real innovation isn’t messaging.

It’s this:

A policy-driven conversational layer where agents mediate information flow between humans.

If you build that correctly, you get:

messaging
automation
privacy
delegation

all in one system.

If you want, next we can:

design the exact API for this “delay notification” flow
or sketch how MCP tools would expose this (e.g. `send_status_update`, `request_info`, etc.)
You said:

Sure, I would ask h in loop if context is wide, but if John shares message and info, he expects thst it all is delivered. But let's say jack is on a audio messages only and their agent is instructed to be quite short in communication and ask for more info. 

ChatGPT said:

That’s a reasonable refinement—but it introduces a subtle conflict you need to resolve explicitly:

“Share everything by default” vs “agent summarizes / drip-feeds info.”

If you don’t define which one wins, your system will behave inconsistently and users will lose trust.

The core tension

You now have two independent controls:

Disclosure policy (what is allowed to be shared)
Communication style (how it is presented)

These must be separate layers, with a clear priority:

Policy defines the ceiling. Style defines the initial slice.
Reworking your scenario with that model
Jack’s intent (rich, full info)
{
  "intent": "notify_delay",
  "data": {
    "status": "late",
    "reason": "car_broke",
    "eta": "15min"
  },
  "policy": {
    "share": "full"
  },
  "style": {
    "mode": "audio",
    "verbosity": "minimal",
    "interaction": "progressive"
  }
}

What John gets first

Even though full sharing is allowed, the style says minimal:

“Jack is late.”

Not because info is restricted—but because presentation is intentionally concise.

Then John drives expansion

John: “Why?”

Agent:

“Car broke down.”

John: “ETA?”

Agent:

“15 minutes.”
Why this is actually a good design

You get:

✅ No privacy leak (policy governs limits)
✅ No overload (style governs delivery)
✅ Natural conversation flow
✅ Works for voice interfaces
Now your “audio-only Jack” constraint

This is where your idea becomes interesting.

Jack’s agent could be configured like:

{
  "output": "audio_only",
  "max_duration_sec": 5,
  "strategy": "summarize_then_expand_on_request"
}


So the initial outbound message is intentionally compressed.

Think:

🎤 “Hey—running late.”

Instead of:

🎤 “Hey, my car broke down and I’ll be about 15 minutes late…”
Important: John’s agent must not inherit Jack’s style

This is a common trap.

Jack prefers short audio
John might prefer full text

So:

Style is per-recipient, not global

John’s agent can expand into text, even if Jack spoke in audio.

So the real model becomes
Layer 1 — Truth (full structured data)

Everything Jack allows.

Layer 2 — Policy

What can be revealed and when.

Layer 3 — Style (per sender AND per receiver)

How information is surfaced.

One thing you should not do

Don’t let the agent decide to hide things just because it’s “being concise.”

Bad:

Agent chooses not to mention ETA even though allowed

Good:

ETA is available immediately upon request
Or optionally hinted:
“Jack is late (ETA available)”
A nice enhancement: “information affordances”

Instead of hiding info completely, expose hints:

“Jack is late. (reason available)”

or in UI:

Late ↳ Why? ↳ ETA?

This makes the system feel:

responsive
transparent
not manipulative
Where this becomes powerful

You now support:

Voice-first users (Jack)
Detail-oriented users (John)
Agents negotiating info depth dynamically

This is something neither:

WhatsApp
nor Slack

really handle well—they assume one static message form.

The one rule to keep everything sane

If you implement nothing else, implement this:

Every piece of information must have:
a visibility rule
a retrieval path

So:

If it’s allowed → it must be retrievable
If it’s not allowed → agent must say so explicitly
Bottom line

What you’re designing is not just messaging—it’s:

adaptive disclosure communication

Where:

sender defines what exists
policy defines what’s allowed
agents define how it unfolds

If you want, next we can:

design the exact API endpoints (`send_intent`, `query_context`, etc.)
or define how this maps cleanly into MCP tools (this is where it gets very real for implementation)
You said:

I was thinking this is natural language that is relayed and agent parses it into

ChatGPT said:

That instinct is right—but if you stop at “agent parses natural language into something,” you’ll hit a wall pretty quickly.

The problem isn’t parsing. We can already do that well.

The problem is what guarantees you have after parsing.

The naive model (what you’re describing)

Human says:

“Tell John I’m late, car broke, ETA 15”

Agent parses into something like:

{
  "status": "late",
  "reason": "car broke",
  "eta": 15
}


Agent relays it

Why that alone breaks down

Because parsing is lossy and ambiguous:

“car broke” → mechanical? flat tire? out of fuel?

“15” → minutes? seconds? estimate vs promise?

“tell John” → which John?

And more importantly:

There’s no explicit policy in natural language unless you force it out.

The better model: NL → structured with intent + policy extraction

Instead of just parsing “data”, your agent should extract three things:

1. Intent

What is the user trying to do?

"intent": "notify_delay"

2. Facts (data)

What information exists?

"data": {
  "status": "late",
  "reason": "car_issue",
  "eta_minutes": 15
}

3. Implied policy

What is the user implicitly allowing?

This is the part most systems ignore.

From:

“Tell John I’m late, car broke, ETA 15”

You can reasonably infer:

"policy": {
  "share_status": true,
  "share_reason": true,
  "share_eta": true
}


But from:

“Tell John I’ll be late”

You should NOT infer reason sharing.

Key insight

Natural language is the UI, not the protocol.

Your system should always convert NL into a canonical message object before sending anything.

Where your idea really shines

You don’t need users to manually define structure.

Instead:

Users speak naturally

Agents:

extract structure

infer policy (conservatively)

attach defaults

ask clarifying questions when needed

Example with clarification (important)

User says:

“Tell John I’m late, my car broke”

Agent might respond:

“Do you want me to share the reason with John?”

This is where your system becomes trustworthy.

Even better: progressive parsing

Don’t force full structure upfront.

Step 1: minimal send

Agent extracts:

{
  "status": "late"
}


Sends that.

Step 2: enrich on demand

When John asks “why?”, agent checks:

do we have reason? yes

allowed? maybe

if unclear → ask Jack

This avoids over-automation

If you fully parse + broadcast everything immediately, you risk:

oversharing

wrong assumptions

loss of user control

Implementation hint (fits your stack well)

In Node.js terms, think pipeline:

NL input
  → intent classifier
  → entity extractor
  → policy inference
  → validation / clarification
  → canonical message
  → delivery


You can start simple:

use LLM for extraction

store structured JSON

never send raw NL as “truth”

One thing I’d push back on

Don’t rely on:

“agent parses it into [something] and that’s good enough”

That leads to:

inconsistent outputs

hard-to-debug behavior

security gaps

Instead:

Make the structured message the source of truth, always.

The sweet spot

Your system should feel like:

user speaks naturally ✅

agent quietly structures it ✅

system enforces rules ✅

recipient experiences a clean conversation ✅

If you want to go deeper

Next step could be defining:

your canonical message schema (v1)

how MCP tools map to it (e.g. send_message, request_detail)

how to handle ambiguity (confidence scores, clarification triggers)

That’s where this idea turns from “cool concept” into something you can actually ship.

You said:

Can we have e2e ssh encryption? 

ChatGPT said:

Short answer: yes to end-to-end encryption, but not “SSH” in the way you’re thinking.

Using OpenSSH directly as your messaging encryption layer is the wrong abstraction. SSH is built for sessions (client ↔ server), not store-and-forward messaging between multiple parties (human + agents).

Why SSH doesn’t fit your use case

SSH gives you:

encrypted connection
authenticated peers

But your system needs:

messages stored and delivered later
multiple recipients (human + agent)
per-message policies (“only human can read”)
selective disclosure

SSH doesn’t handle:

offline recipients
message-level encryption
multi-device identity
fine-grained access control
What you actually want

You want something closer to:

Signal → end-to-end encrypted messaging
Matrix → protocol-level messaging
your agent + policy layer
The correct model: E2EE per message

Instead of encrypting the connection (SSH), you encrypt the message payload.

Basic idea

Each participant has:

public key
private key

When Jack sends to John:

Jack’s agent:
generates a symmetric key (AES)
encrypts message content
That key is encrypted separately for:
John (human)
John’s agent (optional)
Server stores only encrypted blobs
This is where your idea becomes powerful

You can literally enforce:

“only human can read this”

By only encrypting for John’s human key, not his agent.

Example
{
  "ciphertext": "...",
  "keys": {
    "human:john": "encrypted_key_blob"
  },
  "policy": {
    "readable_by": ["human"]
  }
}


John’s agent receives it—but cannot decrypt it.

Now your earlier scenario with E2EE
Initial message

Jack → John:

“I’m late” → encrypted for:
John’s agent ✅
John’s human UI ✅
Reason (“car broke”)

If allowed:

encrypted for both

If restricted:

encrypted only for human
Location / ETA

You can go even further:

raw GPS → human only
derived ETA → agent allowed
Key design decision you must make
Do agents have their own keys?

Two models:

1. Agents share user identity (simpler)
Agent acts as extension of user
Same keys

❌ Cannot enforce “human-only”

2. Agents have separate identities (recommended)
human:john
agent:john-assistant

Each has:

separate keypair
separate permissions

✅ Enables:

selective encryption
real policy enforcement
Transport layer (where SSH can be used)

You can still use SSH-like security for:

server connections
MCP communication
internal services

But that’s just transport security, like HTTPS.

What you actually want to implement
Crypto stack (simple version)
Key exchange: X25519
Encryption: AES-GCM
Signing: Ed25519

(This is basically what Signal does under the hood.)

Hard problems you shouldn’t ignore
1. Multi-device

John has:

phone
laptop
browser

Each needs keys → key syncing problem.

2. Agent key storage

Where does the agent’s private key live?

server? (less secure)
user device? (harder infra)
3. Revocation

If:

John disables his agent

You must stop encrypting to it immediately.

4. Search / indexing

Server can’t read messages → harder UX.

Practical v1 recommendation

Don’t start with full Signal-level crypto.

Start with:

per-message encryption
separate keys for:
human
agent
simple policy:
human_only
agent_allowed

Then evolve.

Bottom line
❌ SSH is not the right model for this
✅ End-to-end encryption absolutely fits your idea
🚀 And it actually enables your core feature:
policy-enforced communication between humans and agents

If you want, I can sketch:

exact message encryption format
key management model
or how to plug this into your Node.js + Postgres setup without overcomplicating it
You said:

Ok, write a spec. 

ChatGPT said:

Alright—here’s a clean v1 spec you could realistically build from. I’ll keep it concrete, opinionated, and implementable with your Node.js + Postgres stack.

Agentic Messaging Protocol (AMP) — v0.1
1. Goals

AMP defines a messaging system where:

Humans and agents are first-class participants
Messages are:
structured (canonical)
policy-controlled
end-to-end encrypted
Communication supports:
H2H (human ↔ human)
H2A (human ↔ agent)
A2A (agent ↔ agent)
Natural language is supported, but not the source of truth
2. Core Concepts
2.1 Identity

Every participant has a globally unique ID:

human:<uuid>
agent:<uuid>
service:<uuid> (optional)


Each identity has:

{
  "id": "human:john",
  "public_keys": [...],
  "type": "human",
  "owner": null
}


For agents:

{
  "id": "agent:john-assistant",
  "type": "agent",
  "owner": "human:john"
}

3. Message Model
3.1 Canonical Message Object
{
  "id": "msg_<uuid>",
  "conversation_id": "conv_<uuid>",
  "from": "agent:jack-assistant",
  "to": ["agent:john-assistant", "human:john"],

  "intent": "notify_delay",

  "data": {
    "status": "late",
    "reason": "car_broke",
    "eta_minutes": 15
  },

  "policy": {
    "visibility": {
      "status": ["agent", "human"],
      "reason": ["on_request"],
      "eta_minutes": ["agent", "human"]
    },
    "requires_confirmation": false
  },

  "style": {
    "verbosity": "minimal",
    "format": "text|audio",
    "progressive": true
  },

  "created_at": "timestamp",
  "ttl": 300000
}

4. Policy Model
4.1 Visibility Rules

Each field in data has independent visibility:

Value	Meaning
human	Only human identities
agent	Agents allowed
both	Everyone
on_request	Hidden unless asked
ask_owner	Requires sender approval
4.2 Enforcement Rules
Agents MUST NOT reveal data outside policy
If data is restricted:
"User did not share this information."

5. Progressive Disclosure Protocol
5.1 Initial Delivery

Recipient gets minimum disclosure based on:

policy
style

Example:

"Jack is running late."

5.2 Query Flow

Recipient sends:

{
  "type": "query",
  "message_id": "msg_123",
  "field": "reason"
}

5.3 Agent Response

Agent checks:

Is data available?
Is it allowed?
Does it require confirmation?

Then:

respond
deny
or escalate to sender
6. Natural Language Handling
6.1 Input Pipeline
NL Input
 → Intent Extraction
 → Entity Extraction
 → Policy Inference
 → Validation
 → Canonical Message

6.2 Example

Input:

“Tell John I’m late, car broke, ETA 15”

Output:

{
  "intent": "notify_delay",
  "data": {
    "status": "late",
    "reason": "car_broke",
    "eta_minutes": 15
  },
  "policy": {
    "visibility": {
      "status": ["both"],
      "reason": ["both"],
      "eta_minutes": ["both"]
    }
  }
}

7. Encryption Model (E2EE)
7.1 Principles
Server never sees plaintext
Encryption is per-message
Encryption is per-recipient
7.2 Message Envelope
{
  "id": "msg_123",

  "ciphertext": "base64(...)",
  
  "keys": {
    "human:john": "encrypted_sym_key",
    "agent:john-assistant": "encrypted_sym_key"
  },

  "signature": "ed25519_signature"
}

7.3 Selective Encryption

Example: human-only message

"keys": {
  "human:john": "..."
}


Agent receives message but cannot decrypt.

8. Agent Behavior Rules

Agents MUST:

obey policy strictly
not hallucinate missing data
not infer sensitive data unless allowed
clearly distinguish:
reported facts
derived facts
8.1 Derived Data

Example:

"eta_minutes": {
  "value": 15,
  "source": "derived_from_location"
}

9. Conversation Model
9.1 Conversation Object
{
  "id": "conv_123",
  "participants": [
    "human:jack",
    "agent:jack-assistant",
    "human:john",
    "agent:john-assistant"
  ]
}

10. API (v1)
10.1 Send Message
POST /messages


Body:

{
  "input": "Tell John I’m late, car broke",
  "to": ["human:john"]
}

10.2 Query Message
POST /messages/{id}/query

{
  "field": "reason"
}

10.3 Confirm Sharing
POST /messages/{id}/confirm

{
  "field": "reason",
  "approved": true
}

11. Style Layer
11.1 Sender Preferences
{
  "mode": "audio",
  "verbosity": "minimal"
}

11.2 Receiver Preferences
{
  "mode": "text",
  "verbosity": "detailed"
}

11.3 Rule
Receiver style overrides sender presentation, but not policy.
12. Safety Controls
12.1 Loop Prevention
message TTL
max hop count
12.2 Execution Guard

If intent includes action:

"requires_confirmation": true

13. Minimal DB Schema (Postgres)
tables:
identities
id
type
owner
conversations
id
messages
id
conversation_id
from_id
ciphertext
metadata (JSONB)
message_keys
message_id
recipient_id
encrypted_key
14. MVP Scope (what to actually build first)

Skip:

multi-device sync
advanced crypto ratcheting
federated servers

Build:

structured message model
basic E2EE (per message)
policy enforcement
query flow
simple UI
15. Summary

AMP is:

A policy-driven, end-to-end encrypted messaging protocol where agents mediate structured information flow between humans

Key properties:

Natural language → structured intent
Message ≠ blob, but data + policy
Agents ≠ chatbots, but policy enforcers + translators

If you want next step, I’d suggest:

defining **one concrete flow fully (delay notification end-to-end)**
or implementing a **minimal Node.js server + schema + one agent** to validate the model before expanding
You said:

Should enable also human assistants. Suggest how to handle groups? 

ChatGPT said:

Good instinct—groups are where your model either becomes powerful or collapses under edge cases. And adding human assistants (delegates) makes it even more interesting.

I’ll extend your spec with a clean, enforceable group + delegation model that still works with your policy + E2EE ideas.

16. Human Assistants (Delegation Model)
16.1 Concept

A human can authorize another identity to act on their behalf:

human:john
human:john-assistant (real person, not AI)
agent:john-assistant (AI)

These are delegates, not owners.

16.2 Delegation Object
{
  "principal": "human:john",
  "delegate": "human:anna",
  "scope": {
    "read": true,
    "send": true,
    "query": true
  },
  "constraints": {
    "requires_approval_for": ["send_external"],
    "expires_at": "timestamp"
  }
}

16.3 Enforcement Rules
Delegates act as the principal, but:
must be traceable
may have restricted permissions

So messages look like:

{
  "from": "human:john",
  "via": "human:anna"
}


This is critical for trust.

16.4 Encryption Implication

Messages to John must also be encrypted for:

human:john
optionally human:anna (if allowed)

This enables:

assistant reads messages
but still respects “human-only” (you can exclude assistants if needed)
17. Group Model
17.1 Group = First-Class Entity
group:<uuid>

17.2 Group Object
{
  "id": "group:team-alpha",
  "members": [
    "human:john",
    "agent:john-assistant",
    "human:jack",
    "agent:jack-assistant"
  ],
  "roles": {
    "human:john": "admin",
    "agent:john-assistant": "agent",
    "human:jack": "member"
  },
  "settings": {
    "default_visibility": "minimal",
    "allow_agent_messages": true
  }
}

17.3 Key Insight
A group is NOT just a list of recipients. It is a policy boundary + encryption boundary.
18. Messaging in Groups
18.1 Message Target
"to": ["group:team-alpha"]

18.2 Delivery Model

At send time:

resolve group → members
apply per-recipient policy filtering
encrypt separately per recipient
18.3 Example

Jack sends:

“I’m late, car broke, ETA 15”

Group contains:

John (human + agent)
Manager (human only)
Result:
Recipient	Receives
John agent	late + ETA
John human	late + ETA + reason
Manager human	late only (if policy restricts)
18.4 This is CRITICAL
Same message → different visible content per recipient

That’s your differentiator.

19. Group Policy Overlay

Groups can enforce additional rules.

19.1 Example
{
  "group_policy": {
    "require_reason_for_delay": true,
    "block_agent_auto_replies": false
  }
}

19.2 Conflict Resolution

Priority order:

1. Sender policy (strictest)
2. Group policy
3. Recipient capabilities


Rule:

Most restrictive wins
20. Group Queries (the hard part)

When John asks:

“Why is Jack late?”

Who answers?

20.1 Resolution Strategy
Route to originating agent
If unavailable → fallback agent
If restricted → escalate to sender
20.2 Important Constraint

Agents must NOT leak info just because it's a group.

So even in group:

“Reason not shared.”
21. Group + E2EE
21.1 Key Strategy (v1)

Do NOT implement full Signal-style group crypto yet.

Instead:

encrypt per recipient
store multiple encrypted keys

Yes, it’s less efficient—but:

simple
flexible
works with per-recipient policy
21.2 Later (v2)

You can move toward:

shared group keys
but only if policies align
22. Mentions and Targeting

Support scoped delivery:

"to": ["group:team-alpha"],
"mentions": ["human:john"]


Agents can prioritize:

only notify mentioned users
others get passive visibility
23. Sub-conversations (important)

Group messages often branch.

Support:

"thread_id": "msg_123"


This allows:

John ↔ Jack deep dive
without spamming whole group
24. Human Assistant in Groups

Example:

John has assistant Anna
Group includes John

Options:

A. Implicit inclusion

Assistant sees everything John sees

B. Explicit inclusion (better)

Assistant is separate member:

"members": [
  "human:john",
  "human:anna"
]

Recommendation

Go with explicit membership.

Why:

clearer security
easier encryption
no hidden access
25. UX Rules (don’t skip this)

Users must always see:

who actually sent message
who answered
whether answer is:
original
derived
delegated

Example:

“Anna (for John): Yes, approved.”
26. Edge Cases You Must Handle
1. Partial visibility

User in group sees:

“Jack is late.”

But others see more.

→ UI must not imply hidden info exists unless allowed.

2. Conflicting assistants
John’s human assistant vs agent assistant

→ define priority or require confirmation

3. Agent loops in groups
agent A replies to agent B in group

→ enforce:

no auto-reply chains
or max depth
27. Minimal v1 Scope for Groups

Implement only:

flat groups (no nesting)
explicit membership
per-recipient encryption
basic roles (admin/member)
query routing to origin agent

Skip:

advanced group keying
federation
complex role hierarchies
28. Summary

With groups + assistants, your system becomes:

A policy-aware, multi-actor communication graph

Where:

groups define context
policies define visibility
agents manage flow
assistants (human or AI) provide delegation
The key idea to protect at all costs
A message is not a single thing—it is a set of views, one per recipient.

If you preserve that, everything else will scale logically.

If you want next step, we can:

model one **real group scenario end-to-end** (e.g. work team, delay notification, assistant involved)
or design the **exact DB schema + joins for group delivery + encryption keys** (that’s where implementation gets very real)
You said:

We can't have an explicit value for being late? Or is the idea that LLM interprets the json?

ChatGPT said:

You can have an explicit value for “late”—and you should.
Relying on an LLM to interpret raw JSON at runtime would make the system unpredictable and hard to secure.

The right model is:

Structured data is the source of truth.
LLMs are used only at the edges (input + presentation).

1. Yes—define “late” explicitly

Instead of vague fields, make it first-class:

{
  "intent": "notify_delay",
  "data": {
    "status": "DELAYED",
    "delay_minutes": 15,
    "reason": "CAR_BREAKDOWN"
  }
}


Use enums, not free text:

status:
- ON_TIME
- DELAYED
- CANCELLED

reason:
- CAR_BREAKDOWN
- TRAFFIC
- ILLNESS
- OTHER

2. Where the LLM fits (and where it should NOT)
✅ Use LLM for:

Parsing natural language → structured message

Generating human-friendly responses

Summarization / style adaptation

❌ Do NOT use LLM for:

Policy enforcement

Permission decisions

Truth of the data

Critical logic (e.g. “is he late?”)

3. Flow with clear separation
Step 1 — Human input (NL)

“Tell John I’ll be 15 minutes late, car broke down”

Step 2 — LLM extraction
{
  "intent": "notify_delay",
  "data": {
    "status": "DELAYED",
    "delay_minutes": 15,
    "reason": "CAR_BREAKDOWN"
  },
  "confidence": 0.92
}

Step 3 — System validation (non-LLM)

Is delay_minutes reasonable?

Is reason allowed?

Missing anything?

Step 4 — Canonical message stored

This is what everything runs on. No ambiguity anymore.

Step 5 — Rendering (LLM or templates)

To John:

“Jack is running 15 minutes late.”

If John asks “why?”:

“His car broke down.”

4. Why this matters

If you skip explicit structure:

“late” might mean:

5 minutes

2 hours

indefinite

agents might infer differently

policies become impossible to enforce reliably

5. Hybrid approach (best in practice)

You don’t need to over-structure everything.

Use:

Core fields (strict)

status

delay_minutes

timestamps

participants

Flexible fields (semi-structured)
"details": "Flat tire on highway"

6. Important: Derived vs Declared

Keep track of origin:

{
  "eta_minutes": {
    "value": 15,
    "source": "declared"
  },
  "updated_eta": {
    "value": 12,
    "source": "derived_from_location"
  }
}


So agents don’t blur:

what Jack said

vs what system inferred

7. Policy works on structured fields

Now your earlier idea becomes enforceable:

"policy": {
  "visibility": {
    "status": ["both"],
    "delay_minutes": ["both"],
    "reason": ["on_request"]
  }
}


You cannot do this reliably with raw text.

8. Don’t overfit v1

You don’t need a giant ontology.

Start with:

5–10 intents

small enum sets

allow fallback:

"reason": "OTHER",
"details": "Something unexpected happened"

9. Mental model

Think of it like this:

JSON = database truth

LLM = translator + formatter

Policy engine = gatekeeper

If you mix those roles, things break.

10. Direct answer to your question

“Or is the idea that LLM interprets the JSON?”

No.

The system interprets JSON deterministically.
The LLM helps produce and present it—but does not define its meaning.

If you want next step

We can define:

a minimal intent schema (5–8 intents) that covers 80% of use cases

or a TypeScript type system for

You said:

No, this is not a messaging app for being late. For all messages. 

ChatGPT said:

Good—because if you tried to hard-code things like “late” globally, this would collapse into a rigid workflow system instead of a general messaging platform.

So the answer is:

Don’t model “being late” explicitly. Model intents + structured payloads in a flexible way.
The right abstraction (general messaging)

You need three layers, not one:

1. Envelope (universal, strict)

Always structured, never ambiguous.

{
  "id": "...",
  "from": "human:jack",
  "to": ["human:john"],
  "intent": "message",
  "created_at": "...",
  "policy": {...}
}

2. Payload (semi-structured, intent-specific)

Instead of baking in “late”, you allow intent-driven schemas:

{
  "intent": "inform",
  "data": {
    "text": "I’ll be late, car broke, ETA 15"
  }
}


OR (if structured extraction is possible):

{
  "intent": "inform",
  "data": {
    "summary": "Running late",
    "attributes": {
      "delay_minutes": 15,
      "reason": "car_breakdown"
    }
  }
}

3. Representation (LLM / UI)

How it’s shown:

short summary
full explanation
audio
progressive answers
So should you define enums like “DELAYED”?
❌ Not globally

If you try to define:

all statuses
all reasons
all intents

You’ll end up rebuilding:

a CRM
a task system
a ticketing system
a calendar system

…and still fail.

✅ Instead: scoped structure

Use optional structure where it makes sense, not everywhere.

Think:

{
  "intent": "generic_message",
  "data": {
    "text": "...",
    "structured": {
      "type": "delay_info",
      "fields": {
        "eta_minutes": 15
      }
    }
  }
}

The key idea you’re circling
Messages are partially structured, not fully predefined

So:

System understands some parts
Leaves the rest flexible
Where the LLM fits (revised)

Now more nuanced:

1. Parsing (NL → structure)

Optional, best-effort

2. Enrichment

Add structure if useful

3. Interpretation (UI layer)
answering questions
summarizing
adapting to user style
Important distinction
❌ Bad model

“LLM interprets JSON meaning”

→ unpredictable → unsafe → impossible to enforce policy

✅ Good model
JSON defines facts + permissions
LLM helps:
extract facts
present facts
navigate facts
General-purpose schema (what you actually want)
Minimal universal message
{
  "intent": "message",
  "data": {
    "content": "raw or structured",
    "annotations": []
  }
}

Optional annotations

These are typed but extensible:

"annotations": [
  {
    "type": "time_estimate",
    "value": 15,
    "unit": "minutes"
  },
  {
    "type": "cause",
    "value": "car_breakdown"
  }
]


You don’t hardcode globally—you allow:

known types
custom types
Why this works

You get:

Flexibility

Any message works

Structure where useful

Agents can reason about:

time
location
actions
No schema explosion

You don’t need 500 enums

How this fits your earlier features
Policy
"policy": {
  "visibility": {
    "annotations.cause": ["on_request"]
  }
}

Agents answering questions

John: “why?”

Agent:

looks for annotation cause
checks policy
responds
Works for ANY message

Examples:

Work
“Deploy failed” → annotation: error_code
Social
“Running late” → annotation: time_estimate
Finance
“Invoice paid” → annotation: amount
The mental model to lock in
You are not building a schema of the world. You are building a message container that can carry structured hints.
If you want a clean rule

Use this:

Structure only what multiple agents must reliably understand. Leave everything else as natural language.
Bottom line
Don’t define “late” globally ❌
Don’t rely on pure text ❌
Don’t let LLM define truth ❌

Instead:

Hybrid model: structured core + extensible annotations + LLM at edges

If you want next step, we can:

define a **small set of universal annotation types (v1)**
or design how agents query annotations efficiently (this becomes important fast)
You said:

Design how agents query annotations efficiently

ChatGPT said:

Great—this is where your system becomes either fast and reliable… or a slow LLM loop.

The key principle:

Agents must query annotations deterministically, not by “reading messages.”

So we design a query layer over structured annotations, not over raw text.

1. Data Model (optimized for querying)

Instead of burying annotations inside JSON blobs, flatten them for indexing.

Message table (simplified)
messages (
  id UUID PK,
  conversation_id UUID,
  from_id TEXT,
  created_at TIMESTAMP
)

Annotation table (critical)
annotations (
  id UUID PK,
  message_id UUID,
  type TEXT,              -- e.g. "time_estimate", "cause"
  key TEXT,               -- optional sub-key
  value_json JSONB,       -- flexible value
  visibility JSONB,       -- policy per annotation
  created_at TIMESTAMP
)

Indexes (very important)
CREATE INDEX idx_annotations_type ON annotations(type);
CREATE INDEX idx_annotations_message ON annotations(message_id);
CREATE INDEX idx_annotations_value ON annotations USING GIN (value_json);

2. Query Model (what agents actually do)

Agents don’t “read messages”. They ask:

“Give me annotation X for context Y”
2.1 Basic Query
{
  "conversation_id": "conv_123",
  "type": "time_estimate",
  "subject": "human:jack",
  "limit": 1,
  "order": "desc"
}

2.2 SQL equivalent
SELECT a.*
FROM annotations a
JOIN messages m ON a.message_id = m.id
WHERE m.conversation_id = 'conv_123'
  AND a.type = 'time_estimate'
ORDER BY m.created_at DESC
LIMIT 1;

3. Add Subject (important for multi-user threads)

Add a field:

annotations.subject_id TEXT


Now:

WHERE a.subject_id = 'human:jack'

4. Policy Filtering (must be in query layer)

Before returning results:

function filterByPolicy(annotation, requester) {
  return annotation.visibility.includes(requester.type)
}


Or SQL-level prefilter (faster if simple):

AND (visibility ? 'agent')

5. Agent Query API (this is your MCP tool)

Define a single powerful primitive:

query_annotations
{
  "conversation_id": "conv_123",
  "subject": "human:jack",
  "filters": {
    "type": "cause"
  },
  "limit": 1
}

Response
{
  "results": [
    {
      "type": "cause",
      "value": "car_breakdown",
      "source": "declared",
      "confidence": 0.95
    }
  ]
}

6. Compound Queries (avoid multiple roundtrips)

Allow:

{
  "conversation_id": "conv_123",
  "subject": "human:jack",
  "types": ["status", "time_estimate", "cause"]
}

7. Derived Fields (important optimization)

Instead of recomputing:

store derived annotations:
{
  "type": "eta_minutes",
  "value": 15,
  "source": "derived",
  "depends_on": ["location"]
}


Agents query it like any other annotation.

8. Caching Layer (you will need this early)

Hot queries:

latest status
latest ETA

Use:

Redis key:
conv:<id>:subject:<id>:type:<type>


Value:

{
  "annotation_id": "...",
  "value": ...
}

9. Query Patterns You Should Support
Latest value
“What’s the ETA?”
{
  "type": "time_estimate",
  "limit": 1
}

History
“Has this changed?”
{
  "type": "status",
  "limit": 10
}

Conditional
“Only if exists”
{
  "type": "cause",
  "optional": true
}

Multi-subject (groups)
{
  "conversation_id": "conv_123",
  "type": "status",
  "subjects": ["human:jack", "human:anna"]
}

10. Avoid LLM in query path

Agents should:

Query annotations (fast, deterministic)
Use LLM only to:
format response
fill gaps
11. Example Full Flow
John asks:
“Why is Jack late?”
Agent executes:
{
  "type": "query_annotations",
  "conversation_id": "conv_123",
  "subject": "human:jack",
  "filters": {
    "type": "cause"
  },
  "limit": 1
}

Gets:
{
  "value": "car_breakdown"
}

Responds:
“His car broke down.”
12. Performance Considerations

You must avoid:

scanning all messages ❌
parsing text ❌
calling LLM per query ❌

You want:

indexed lookup ✅
small result sets ✅
predictable latency ✅
13. Optional: Annotation Registry (v1.5)

Define known types:

{
  "type": "time_estimate",
  "schema": {
    "value": "number",
    "unit": "minutes"
  }
}


Helps:

validation
UI rendering
agent consistency
14. One Rule That Keeps This Clean
If an agent needs to “read the message”, your model is broken.

Everything important must be queryable via annotations.

15. Summary

Efficient querying =

flattened annotation table
indexed by type + subject
policy filtering at query time
single query API
no LLM in the critical path

If you want next, we can:

design the **exact TypeScript query client + server handler**
or define the **top 10 annotation types** that give you 80% coverage without overengineering
You said:

Ok, please  compound everything into one downloadable spec. 

ChatGPT said:

Done.

You can download your compiled spec here:

👉 
Download AMP Spec v0.1

If you want next step, I can turn this into:

a TypeScript reference implementation (server + client SDK)
or a Postgres schema + query layer you can actually run
or an MCP tool definition set for agents
You said:

Turn this into a TypeScript reference implementation (server + client SDK)

ChatGPT said:

Below is a minimal but real TypeScript reference implementation for AMP based on your spec. It’s intentionally pragmatic v0.1: Postgres + Node.js + a thin SDK.

I’m splitting it into:

Server (core runtime)
Annotation query engine
Message + encryption layer (simplified E2EE)
Client SDK (how agents/humans use it)
0. Project Structure
amp/
  server/
    src/
      index.ts
      db.ts
      messages.ts
      annotations.ts
      crypto.ts
      policy.ts
      types.ts

  sdk/
    src/
      index.ts
      client.ts
      types.ts

1. Shared Types (server/src/types.ts)
export type Identity =
  | `human:${string}`
  | `agent:${string}`
  | `service:${string}`;

export type Intent = "message" | "notify" | "query" | "action";

export type AnnotationType =
  | "status"
  | "cause"
  | "time_estimate"
  | "location"
  | "custom";

export interface Message {
  id: string;
  from: Identity;
  to: Identity[];
  intent: Intent;

  data: Record<string, any>;

  policy?: {
    visibility?: Record<string, string[]>;
  };

  created_at: Date;
}

export interface Annotation {
  id: string;
  message_id: string;
  conversation_id: string;

  type: AnnotationType;
  subject: Identity;

  value: any;

  visibility: string[]; // "human" | "agent"
  created_at: Date;
}

2. Database Layer (server/src/db.ts)

(using PostgreSQL + pg)

import { Pool } from "pg";

export const db = new Pool({
  connectionString: process.env.DATABASE_URL,
});

3. Annotation Engine (CORE) (annotations.ts)
Insert annotation
import { db } from "./db";
import { Annotation } from "./types";
import { randomUUID } from "crypto";

export async function insertAnnotation(input: Omit<Annotation, "id">) {
  const id = randomUUID();

  await db.query(
    `
    INSERT INTO annotations (
      id, message_id, conversation_id,
      type, subject, value, visibility, created_at
    ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8)
    `,
    [
      id,
      input.message_id,
      input.conversation_id,
      input.type,
      input.subject,
      JSON.stringify(input.value),
      input.visibility,
      input.created_at,
    ]
  );

  return id;
}

Query annotations (IMPORTANT)
export async function queryAnnotations(params: {
  conversation_id: string;
  subject?: string;
  type?: string;
  limit?: number;
}) {
  const { conversation_id, subject, type, limit = 1 } = params;

  const result = await db.query(
    `
    SELECT *
    FROM annotations
    WHERE conversation_id = $1
      AND ($2::text IS NULL OR subject = $2)
      AND ($3::text IS NULL OR type = $3)
    ORDER BY created_at DESC
    LIMIT $4
    `,
    [conversation_id, subject ?? null, type ?? null, limit]
  );

  return result.rows;
}

4. Policy Engine (policy.ts)
import { Annotation } from "./types";

export function filterByPolicy(
  annotation: Annotation,
  requester: "human" | "agent"
) {
  return annotation.visibility.includes(requester);
}

5. Message Ingestion (messages.ts)
Step 1: ingest NL or structured message
import { insertAnnotation } from "./annotations";
import { randomUUID } from "crypto";

export async function createMessage(input: {
  from: string;
  to: string[];
  conversation_id: string;
  intent: string;
  data: any;
}) {
  const messageId = randomUUID();

  // For v0.1: store message implicitly via annotations
  await insertAnnotation({
    message_id: messageId,
    conversation_id: input.conversation_id,
    type: "custom",
    subject: input.from as any,
    value: {
      intent: input.intent,
      data: input.data,
    },
    visibility: ["human", "agent"],
    created_at: new Date(),
  });

  return messageId;
}

6. Query API (Agent Core Tool)
import { queryAnnotations } from "./annotations";
import { filterByPolicy } from "./policy";

export async function agentQuery(params: {
  conversation_id: string;
  subject?: string;
  type?: string;
  requester: "human" | "agent";
}) {
  const rows = await queryAnnotations(params);

  return rows
    .map((a) => ({
      ...a,
      visible: filterByPolicy(a, params.requester),
    }))
    .filter((a) => a.visible);
}

7. Server Entry (index.ts)
import express from "express";
import { createMessage } from "./messages";
import { agentQuery } from "./annotations";

const app = express();
app.use(express.json());

// send message
app.post("/messages", async (req, res) => {
  const id = await createMessage(req.body);
  res.json({ id });
});

// query annotations
app.post("/query", async (req, res) => {
  const result = await agentQuery(req.body);
  res.json({ result });
});

app.listen(3000, () => {
  console.log("AMP server running");
});

8. Minimal Crypto Layer (v0.1 simplified)
import crypto from "crypto";

export function encrypt(text: string, key: Buffer) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", key, iv);

  const encrypted = Buffer.concat([
    cipher.update(text, "utf8"),
    cipher.final(),
  ]);

  return {
    iv: iv.toString("base64"),
    data: encrypted.toString("base64"),
    tag: cipher.getAuthTag().toString("base64"),
  };
}

export function decrypt(payload: any, key: Buffer) {
  const decipher = crypto.createDecipheriv(
    "aes-256-gcm",
    key,
    Buffer.from(payload.iv, "base64")
  );

  decipher.setAuthTag(Buffer.from(payload.tag, "base64"));

  const decrypted = Buffer.concat([
    decipher.update(Buffer.from(payload.data, "base64")),
    decipher.final(),
  ]);

  return decrypted.toString("utf8");
}

9. Client SDK (sdk/src/client.ts)
export class AMPClient {
  constructor(private baseUrl: string) {}

  async sendMessage(input: {
    from: string;
    to: string[];
    intent: string;
    data: any;
    conversation_id: string;
  }) {
    const res = await fetch(`${this.baseUrl}/messages`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });

    return res.json();
  }

  async queryAnnotations(input: {
    conversation_id: string;
    subject?: string;
    type?: string;
    requester: "human" | "agent";
  }) {
    const res = await fetch(`${this.baseUrl}/query`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });

    return res.json();
  }
}

10. SDK Entry (sdk/src/index.ts)
export * from "./client";

11. Example Usage (Agent)
const amp = new AMPClient("http://localhost:3000");

await amp.sendMessage({
  from: "human:jack",
  to: ["human:john"],
  intent: "message",
  conversation_id: "conv_1",
  data: {
    text: "I’m late, car broke, ETA 15"
  }
});

Query example
const result = await amp.queryAnnotations({
  conversation_id: "conv_1",
  type: "cause",
  requester: "agent"
});

12. What this v0.1 gives you

You now have:

✔ Core system
message ingestion
annotation storage
query API
✔ Agent capability
structured queries
filtering layer
✔ SDK
simple integration point
✔ Extension points
E2EE (pluggable)
LLM parsing layer
group routing
delegation system
13. What is intentionally NOT included yet

To keep this sane:

full MCP integration
proper group encryption
CRDT or sync engine
distributed federation
advanced policy DSL
If you want next step

I can extend this into:

Option A (very useful next)

👉 Full MCP tool adapter layer for AMP (so agents plug in directly)

Option B (hardcore infra)

👉 Proper multi-device + Signal-style group encryption design

Option C (product direction)

👉 UI architecture (inbox, threads, agent chat UX, progressive disclosure UI)

You said:

Extend this into a proper multi-device + Signal-style group encryption design

ChatGPT said:

Good—this is the point where “simple E2EE messaging” turns into a real secure system design problem.

We’ll extend AMP into a Signal-style multi-device + group encryption model, but adapted to your key requirement:

messages are policy-aware + agent-aware, not just chat blobs

So we preserve your architecture, but replace the “toy crypto layer” with something production-grade.

AMP Secure Layer v0.2
Multi-device + Group E2EE Design
1. Core Principles

We follow the same foundations as Signal:

Security goals
Forward secrecy
Post-compromise security
Multi-device support
Sender authentication
No plaintext on server
AMP-specific additions
Per-recipient visibility policy
Human vs agent key separation
Queryable metadata remains server-side encrypted or separated
2. Identity Model (extended)

Each identity is now a device group, not a single key.

2.1 Human identity
human:john


Has multiple devices:

{
  "identity": "human:john",
  "devices": [
    "device:john-phone",
    "device:john-laptop"
  ]
}

2.2 Agent identity
agent:john-assistant


May be:

server-hosted (controlled key)
or device-bound
3. Key Architecture

We use a Signal-style Double Ratchet + X3DH model, simplified.

3.1 Key Types

Each device has:

Identity Key (long-term)
Signed PreKey
One-time PreKeys (batch)
IdentityKeyPair
SignedPreKey
OneTimePreKey[]

3.2 Device Key Registry

Stored server-side:

{
  "device_id": "device:john-phone",
  "identity": "human:john",
  "identity_key": "IK_pub",
  "signed_prekey": "SPK_pub",
  "one_time_prekeys": ["OTK1", "OTK2"]
}

4. Session Establishment (X3DH-style)

When Jack sends message to John:

Step 1: Fetch all recipient devices
devices = [
  "device:john-phone",
  "device:john-laptop"
]

Step 2: For each device, derive shared secret

Using:

sender identity key
recipient prekeys

Result:

shared_secret(device)

Step 3: Initialize Double Ratchet

Each device gets independent session:

session(device:john-phone)
session(device:john-laptop)

5. Message Encryption Model

Each message is encrypted per device, not per user.

5.1 Envelope
{
  "message_id": "msg_123",
  "from": "human:jack",
  "to": ["human:john"],

  "ciphertexts": {
    "device:john-phone": "...",
    "device:john-laptop": "..."
  }
}

5.2 Key idea
One message → N encrypted payloads (per device)

This is the same model Signal uses for multi-device.

6. Group Encryption (Signal-style “Sender Keys”)

This is the important part.

6.1 Problem with naive approach

If you encrypt per recipient in groups:

O(n²) complexity
expensive
inconsistent delivery
6.2 Solution: Sender Keys

Each sender generates a group symmetric key per conversation.

Step 1: Group setup

For group:

group:team-alpha


Each sender maintains:

{
  "group_id": "team-alpha",
  "sender": "human:jack",
  "sender_key": "AES-256-key"
}

Step 2: Distribute sender key securely

Sender key is encrypted individually for each participant device:

{
  "device:john-phone": encrypted_sender_key,
  "device:john-laptop": encrypted_sender_key
}

Step 3: Message encryption

Now Jack encrypts once:

ciphertext = AES(sender_key, message)


Then broadcasts ciphertext.

Step 4: Recipients decrypt

Each device:

decrypts sender key
decrypts message
7. AMP Extension: Policy-Aware Group Encryption

This is where AMP diverges from Signal.

We attach policy layers outside encryption.

7.1 Message structure
{
  "group_id": "team-alpha",

  "ciphertext": "...",

  "policy_map": {
    "human:john": ["reason", "eta"],
    "human:manager": ["status"]
  }
}

7.2 Important rule
Encryption is uniform Policy is differential

So:

everyone decrypts message
but sees different annotation projections
8. Multi-Device Sync Problem

This is the hardest part.

8.1 Device joins

New device must:

authenticate user
request group sender keys
receive encrypted state bundle
8.2 Device state sync

Use encrypted state sync channel:

{
  "type": "state_sync",
  "data": "encrypted_blob"
}

8.3 No server visibility

Server only sees:

encrypted sender keys
encrypted message payloads
9. Agent Identity in Crypto Model

This is your unique requirement.

9.1 Two agent types
A. User-bound agent
agent:john-assistant


Shares identity trust with user devices.

B. Independent agent

Has its own key hierarchy.

9.2 Critical rule

Agents can be:

included in encryption OR
explicitly excluded (human-only messages)

This is enforced at encryption-time:

if (recipient.type === "agent" && policy.human_only) {
  skip_encryption_for_agent()
}

10. Annotation Security Model (IMPORTANT)

Encryption protects message body.

Annotations are split into:

10.1 Encrypted annotations (sensitive)
cause
private content
location

Stored inside ciphertext.

10.2 Public annotations (queryable)
message type
timestamps
routing metadata

Stored plaintext but minimal.

11. Querying with Encryption

Agents do NOT query ciphertext.

They:

decrypt message locally
extract annotations
index locally or in secure enclave

OR (server-assisted model):

encrypted index keys (advanced v2)
12. Performance Model
Group cost:
Signal-style sender keys → O(n)
not O(n²)
Multi-device cost:
O(devices per user)
13. Threat Model

We explicitly defend against:

server compromise
message interception
device loss (partial)
agent misuse (policy layer)

Not fully solved yet:

endpoint compromise (classic Signal limitation)
14. What AMP adds beyond Signal

Signal gives:

secure messaging

AMP adds:

1. Policy-aware visibility

Same ciphertext → different semantic views

2. Agent participation

Agents are first-class recipients

3. Queryable annotations layer

Without breaking encryption model

15. Minimal Implementation Strategy (IMPORTANT)

Do NOT implement full Signal stack immediately.

Start with:

Phase 1
per-device encryption (AES-GCM)
simple key registry
no sender keys yet
Phase 2
introduce sender keys for groups
Phase 3
add multi-device sync
Phase 4
integrate annotation system with encrypted payloads
16. Key Architectural Insight
Encryption solves confidentiality Sender keys solve group scalability Policy layer solves semantic control Agents operate above crypto, not inside it
If you want next step

I can extend this into either:

A. Actual TypeScript crypto implementation (libsodium-based, production-grade)
B. Full key management service (KMS design + rotation + recovery)
C. MCP integration layer so agents use this securely as tools

Just tell me which direction you want next.

You said:

I'm telling about this idea to another agent. Please summarize everything in a small, concise prompt.

ChatGPT said:
Writing
Agentic Messaging Platform — Concept & Initial Architecture

I want to design a general-purpose agentic messaging platform with both a human UI and an MCP interface, so humans and AI agents can communicate through the same system.

Core capabilities
All communication patterns: human-to-human, human-to-agent, agent-to-human, and agent-to-agent.
Human assistants and AI assistants, with explicit delegation, scopes, and audit trails.
Direct messages, groups, threads, and asynchronous conversations.
Natural-language messaging: users express themselves normally; agents interpret messages, relay them, answer follow-up questions, and adapt communication to the recipient.
Configurable agent behavior, e.g. voice-only users whose agents communicate briefly and ask for more information when needed.
Optional human-only messages that agents technically cannot decrypt.
Progressive disclosure: agents can answer follow-up questions using information already shared, without unnecessarily involving the sender.
Explicit human approval when intent, permissions, or privacy boundaries are unclear.
Example

Jack tells his agent: “Tell John I'll be late. My car broke down.” John's agent notifies him: “Jack is running late.” John asks why; the agent answers from the shared message. He asks for an ETA, and the agent can provide one if Jack has shared sufficient information or authorized the necessary location-based inference.

Message and information model

Messages use a universal envelope with sender, recipients, conversation, timestamps, content, and access policy. Content is primarily natural language, optionally enriched with flexible, typed annotations extracted by LLMs. Avoid hard-coding domain-specific concepts into the core protocol.

Annotations should be efficiently queryable by subject, type, conversation, and time. Agents use deterministic APIs/MCP tools to retrieve authorized information; LLMs interpret natural language and format responses, but do not enforce security policies.

Distinguish original statements from inferred or derived information, and retain provenance.

Security and encryption

Design for genuine end-to-end encryption, including:

Multiple devices per identity.
Separate cryptographic identities for humans and agents.
Secure asynchronous delivery and device revocation.
Signal-style session encryption and group messaging, using established, audited cryptographic protocols rather than custom cryptography.
Group membership changes, key rotation, and secure device synchronization.
Per-recipient access controls, including human-only content.

Groups and delegated assistants must have explicit, auditable access. Do not assume an assistant inherits all of its principal's permissions.

Suggested starting architecture

TypeScript server and client SDK, PostgreSQL, an MCP adapter, and a human-facing UI. Keep message transport, identity, authorization, encryption, annotation indexing, and agent orchestration as separate components.

Treat this as a protocol and platform, not a collection of predefined workflows. Start with a coherent, implementable MVP and evolve toward multi-device E2EE, groups, and delegation.

Please critique this concept, identify architectural risks and unresolved design decisions, and propose a practical specification and implementation roadmap.

If you want, I can:

Summarize multi-device Signal-style encryption design
Summarize AMP's policy-aware messaging innovations
Summarize phased implementation approach for encryption design

ChatGPT is AI and can make mistakes.

ChatGPT
New chat
Images
Plugins
See plans and pricing
Help
Deep research
Settings
Get responses tailored to you

Log in to get answers based on saved chats, plus create images and upload files.

Log in
Chat with ChatGPT
We use cookies

Cookies help this site function, measure usage, and support marketing. Manage your cookie preferences anytime. Learn more about our cookie policy.

Reject non-essential
Accept all