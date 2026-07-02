package com.hotpath.heatmap

import com.hotpath.heatmap.analysis.CallGraphTraversal
import com.hotpath.heatmap.analysis.JsLanguageSupport
import com.hotpath.heatmap.analysis.MethodSummaryService
import com.hotpath.heatmap.model.Severity
import com.hotpath.heatmap.model.ThresholdPreset
import com.hotpath.heatmap.settings.HotPathSettings
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JsLanguageSupportTest : BasePlatformTestCase() {

    private val support = JsLanguageSupport()

    override fun setUp() {
        super.setUp()
        // These assertions are written against the original (now "Low") thresholds.
        HotPathSettings.getInstance().state.thresholdPreset = ThresholdPreset.LOW
    }

    private val code = """
        class UserRepository {
            findById(id: number): string { return ''; }
            findAll(): string[] { return []; }
        }

        class ReportService {
            constructor(private repo: UserRepository) {}

            aggregate(): void {
                this.repo.findAll().forEach(u => {
                    this.repo.findById(1);
                });
            }

            cheap(): number { return 1 + 1; }
        }

        class Controller {
            constructor(private reports: ReportService) {}

            expensiveInLoop(items: number[]): void {
                items.forEach(i => {
                    this.reports.aggregate();
                });
            }

            cheapCall(): number { return this.reports.cheap(); }

            httpCall(): void { fetch('https://example.com'); }
        }
    """.trimIndent()

    private fun traversal(): CallGraphTraversal {
        val settings = HotPathSettings.getInstance().state
        val summaryService = project.getService(MethodSummaryService::class.java)
        return CallGraphTraversal(project, settings, summaryService, support)
    }

    private fun callNamed(file: PsiFile, name: String): PsiElement =
        support.collectCallSites(file).first { support.callName(it) == name }

    private fun severityOf(file: PsiFile, calleeName: String): Severity? =
        traversal().analyzeCallSite(callNamed(file, calleeName), Long.MAX_VALUE)?.severity

    fun `test repository access inside forEach loop is high`() {
        val file = myFixture.configureByText("a.ts", code)
        // expensiveInLoop -> aggregate() (in a forEach) -> findAll/findById on a *Repository.
        val severity = severityOf(file, "aggregate")
        requireNotNull(severity) { "this.reports.aggregate() should resolve" }
        assertTrue("expected at least HIGH but was $severity", severity.ordinal >= Severity.HIGH.ordinal)
    }

    fun `test trivial method call is not highlighted`() {
        val file = myFixture.configureByText("a.ts", code)
        assertEquals(Severity.NONE, severityOf(file, "cheap"))
    }

    fun `test direct fetch call is flagged as io`() {
        val file = myFixture.configureByText("a.ts", code)
        val result = traversal().analyzeCallSite(callNamed(file, "fetch"), Long.MAX_VALUE)
        requireNotNull(result) { "fetch(...) should produce an I/O result" }
        assertTrue(result.severity.isHighlighted)
        assertTrue(result.breakdown.reasons.any { it.contains("I/O") })
    }
}
