package com.agenticmessenger.mcp

import com.agenticmessenger.agent.AmpAgentService
import com.agenticmessenger.agent.ContactDescriptor
import fi.italeino.mcp.binder.BinderTransport
import fi.italeino.mcp.binder.CallerIdentity
import fi.italeino.mcp.binder.ConnectionRefusedException
import fi.italeino.mcp.binder.StreamLink
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AmpMcpBinderProviderTest {
    private val cert = "ab".repeat(32)
    private val aidos = CallerIdentity(10_050, setOf("fi.italeino.aidos"), setOf(cert))
    private val aidosKey = ApprovedCaller("fi.italeino.aidos", cert)

    private fun socketPair(): Pair<Socket, Socket> = ServerSocket(0).use { s -> Socket("127.0.0.1", s.localPort) to s.accept() }

    private fun link(s: Socket) = StreamLink(s.getInputStream(), s.getOutputStream(), 1 shl 20, shutdown = {
        runCatching { s.close() }
    })

    private val sent = mutableListOf<Boolean>()
    private val service = AmpAgentService(
        contactLookup = { ContactDescriptor("bob", "Bob", "mbx", "did:aidos:bob") },
        messageSender = { _, _, ghostwrite -> sent += ghostwrite; true },
    )

    private fun provider(approvals: CallerApprovalStore, pending: MutableList<ApprovedCaller> = mutableListOf()) =
        AmpMcpBinderProvider({ service.toolDefinitions }, approvals, { pending += it })

    private fun <T> session(p: AmpMcpBinderProvider, caller: CallerIdentity, block: suspend (Client) -> T): T = runBlocking {
        val (c, s) = socketPair()
        p.host.accept(caller, link(s))
        val client = Client(Implementation("aidos-test", "1"))
        try {
            withTimeout(15_000) {
                client.connect(BinderTransport(link(c)))
                block(client)
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `an unknown caller is refused and the user is asked`() {
        val pending = mutableListOf<ApprovedCaller>()
        val p = provider(InMemoryCallerApprovals(), pending)
        val (_, s) = socketPair()
        assertFailsWith<ConnectionRefusedException> { p.host.accept(aidos, link(s)) }
        assertEquals(listOf(aidosKey), pending)
        assertEquals(0, p.host.activeSessions)
    }

    @Test
    fun `an approved caller reads a chat tool over the transport`() {
        val approvals = InMemoryCallerApprovals().apply { approve(aidosKey) }
        val text = session(provider(approvals), aidos) {
            assertEquals(
                setOf("get_contact_status", "query_chat_messages", "send_chat_message"),
                it.listTools().tools.map { t -> t.name }.toSet(),
            )
            (it.callTool("get_contact_status", buildJsonObject { put("contactName", "Bob") }).content.single() as TextContent).text
        }
        assertTrue(text.isNotBlank())
    }

    @Test
    fun `a denied caller stays out and is not asked again`() {
        val pending = mutableListOf<ApprovedCaller>()
        val approvals = InMemoryCallerApprovals().apply { deny(aidosKey) }
        val p = provider(approvals, pending)
        val (_, s) = socketPair()
        assertFailsWith<ConnectionRefusedException> { p.host.accept(aidos, link(s)) }
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `the same package with a different signing key is a different, undecided caller`() {
        val approvals = InMemoryCallerApprovals().apply { approve(aidosKey) }
        val pending = mutableListOf<ApprovedCaller>()
        val p = provider(approvals, pending)
        val impostor = aidos.copy(signingCertSha256 = setOf("cd".repeat(32)))
        val (_, s) = socketPair()
        assertFailsWith<ConnectionRefusedException> { p.host.accept(impostor, link(s)) }
        assertEquals(listOf(ApprovedCaller("fi.italeino.aidos", "cd".repeat(32))), pending)
    }

    @Test
    fun `unverified identity is never admitted or queued`() {
        val approvals = InMemoryCallerApprovals().apply { approve(aidosKey) }
        val pending = mutableListOf<ApprovedCaller>()
        val p = provider(approvals, pending)
        for (unverified in listOf(aidos.copy(signingCertSha256 = emptySet()), aidos.copy(packages = emptySet()))) {
            val (_, s) = socketPair()
            assertFailsWith<ConnectionRefusedException> { p.host.accept(unverified, link(s)) }
        }
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `revoking approval stops new sessions`() {
        val approvals = InMemoryCallerApprovals().apply { approve(aidosKey) }
        val p = provider(approvals)
        session(p, aidos) { it.listTools() }
        approvals.revoke(aidosKey)
        val (_, s) = socketPair()
        assertFailsWith<ConnectionRefusedException> { p.host.accept(aidos, link(s)) }
    }

    @Test
    fun `an approved send goes out as the agent delegate, never as the human, by default`() {
        sent.clear()
        val approvals = InMemoryCallerApprovals().apply { approve(aidosKey) }
        session(provider(approvals), aidos) {
            it.callTool("send_chat_message", buildJsonObject {
                put("recipientName", "Bob"); put("messageText", "hi"); put("ghostwrite", true)
            })
        }
        assertEquals(listOf(false), sent, "ghostwrite must be ignored when allowSendAsHuman is false")
    }
}
