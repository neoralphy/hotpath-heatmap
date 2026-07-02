package com.hotpath.heatmap

import com.hotpath.heatmap.analysis.CallGraphTraversal
import com.hotpath.heatmap.analysis.MethodSummaryService
import com.hotpath.heatmap.analysis.PhpLanguageSupport
import com.hotpath.heatmap.model.Severity
import com.hotpath.heatmap.model.ThresholdPreset
import com.hotpath.heatmap.settings.HotPathSettings
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.FunctionReference

class CallGraphTraversalTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // These assertions are written against the original (now "Low") thresholds.
        HotPathSettings.getInstance().state.thresholdPreset = ThresholdPreset.LOW
    }

    private val fixtureCode = """
        <?php
        namespace Demo;

        class UserRepository {
            public function findActiveUsers(): array { return []; }
            public function findOrdersFor(int ${'$'}userId): array { return []; }
        }

        class MailClient {
            public function send(string ${'$'}to, string ${'$'}body): void {}
        }

        class InvoiceService {
            public function __construct(
                private UserRepository ${'$'}users,
                private MailClient ${'$'}mail,
            ) {}

            public function buildInvoices(): void {
                foreach (${'$'}this->users->findActiveUsers() as ${'$'}user) {
                    foreach (${'$'}this->users->findOrdersFor(${'$'}user) as ${'$'}order) {
                        ${'$'}this->mail->send('x@y.z', 'invoice');
                    }
                }
            }
        }

        class BillingService {
            public function __construct(private InvoiceService ${'$'}invoices) {}
            public function calculate(): void { ${'$'}this->invoices->buildInvoices(); }
        }

        class OrderController {
            public function __construct(private BillingService ${'$'}billing) {}

            public function cheapLooking(array ${'$'}items): void {
                foreach (${'$'}items as ${'$'}item) {
                    ${'$'}this->billing->calculate();
                }
            }

            public function noop(): void {}
            public function callsNoop(): void { ${'$'}this->noop(); }
        }
    """.trimIndent()

    private fun traversal(): CallGraphTraversal {
        val settings = HotPathSettings.getInstance().state
        val summaryService = project.getService(MethodSummaryService::class.java)
        return CallGraphTraversal(project, settings, summaryService, PhpLanguageSupport())
    }

    private fun callRefNamed(file: PsiFile, name: String): FunctionReference =
        PsiTreeUtil.findChildrenOfType(file, FunctionReference::class.java)
            .first { it.name == name }

    fun `test expensive nested call is flagged very high`() {
        val file = myFixture.configureByText("hotpath.php", fixtureCode)
        val ref = callRefNamed(file, "calculate")

        val result = traversal().analyzeCallSite(ref, deadlineNanos = Long.MAX_VALUE)
        requireNotNull(result) { "calculate() should resolve and produce a result" }

        assertEquals(Severity.VERY_HIGH, result.severity)
        assertTrue("score should clear the very-high band", result.score >= 13)

        // The breakdown should explain *why*: loop context, repo + client downstream.
        val reasons = result.breakdown.reasons.joinToString("\n")
        assertTrue("expected loop reason in: $reasons", reasons.contains("inside loop depth 1"))
        assertTrue("expected repository reason in: $reasons", reasons.contains("Repository-like"))
        assertTrue("expected client reason in: $reasons", reasons.contains("Client/gateway-like"))
    }

    fun `test trivial call is not highlighted`() {
        val file = myFixture.configureByText("hotpath.php", fixtureCode)
        val ref = callRefNamed(file, "noop")

        val result = traversal().analyzeCallSite(ref, deadlineNanos = Long.MAX_VALUE)
        requireNotNull(result) { "noop() should resolve" }

        assertEquals(Severity.NONE, result.severity)
        assertFalse(result.severity.isHighlighted)
    }

    fun `test client helper calling only builtins is not high`() {
        // Regression: a pure private helper in a *Client class whose only calls are PHP built-ins
        // (sprintf/mt_rand) must not be flagged high. Built-ins are leaves, not downstream cost.
        val code = """
            <?php
            namespace Demo;
            class OpenMeterClient {
                public function makeId(): string {
                    return sprintf(
                        '%04x-%04x-%04x',
                        mt_rand(0, 9), mt_rand(0, 9), mt_rand(0, 9),
                        mt_rand(0, 9), mt_rand(0, 9), mt_rand(0, 9)
                    );
                }
                public function caller(): string { return ${'$'}this->makeId(); }
            }
        """.trimIndent()
        val file = myFixture.configureByText("client.php", code)
        val result = traversal().analyzeCallSite(callRefNamed(file, "makeId"), Long.MAX_VALUE)!!

        assertEquals("built-in calls must not count as fan-out", 0, result.breakdown.fanOut)
        assertTrue(
            "expected at most LOW but was ${'$'}{result.severity} (score ${'$'}{result.score}): ${'$'}{result.breakdown.reasons}",
            result.severity.ordinal <= Severity.LOW.ordinal,
        )
    }

    fun `test depth cap truncates and marks approximate`() {
        // maxCallDepth = 1 means we cannot descend past the immediate target.
        val settings = HotPathSettings.getInstance().state
        val original = settings.maxCallDepth
        try {
            settings.maxCallDepth = 1
            val file = myFixture.configureByText("hotpath.php", fixtureCode)
            val ref = callRefNamed(file, "calculate")

            val result = traversal().analyzeCallSite(ref, deadlineNanos = Long.MAX_VALUE)
            requireNotNull(result)
            assertTrue("deep graph past depth 1 should be truncated", result.breakdown.truncated)
        } finally {
            settings.maxCallDepth = original
        }
    }
}
