package com.hotpath.heatmap

import com.hotpath.heatmap.analysis.CallGraphTraversal
import com.hotpath.heatmap.analysis.MethodSummaryService
import com.hotpath.heatmap.analysis.PhpLanguageSupport
import com.hotpath.heatmap.model.Severity
import com.hotpath.heatmap.settings.HotPathSettings
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.Method
import java.io.File

/**
 * Loads the real `hotpath-testproject` files and asserts every showcase call site lands in the
 * band its method name promises. This keeps the test project honest: if the heuristics drift,
 * these assertions (and the README table) break.
 */
class ShowcaseProjectTest : BasePlatformTestCase() {

    private companion object {
        const val TEST_PROJECT = "/Users/neoralphy/VCS/hotpath-testproject"
        val FILES = listOf(
            "src/Model/Order.php",
            "src/Model/OrderLine.php",
            "src/Repository/UserRepository.php",
            "src/Repository/OrderRepository.php",
            "src/Client/MailClient.php",
            "src/Service/PricingService.php",
            "src/Service/ReportService.php",
            "src/Controller/HeatmapShowcaseController.php",
            "vendor/acme/huge/src/HugeLibrary.php",
            "tests/ReportServiceTest.php",
        )
    }

    private lateinit var controller: PsiFile

    /** The sibling test project is optional; when it's absent (e.g. a fresh clone), these skip. */
    private val available: Boolean get() = File(TEST_PROJECT).isDirectory

    override fun setUp() {
        super.setUp()
        if (!available) return
        var controllerFile: PsiFile? = null
        for (rel in FILES) {
            val psi = myFixture.addFileToProject(rel, File("$TEST_PROJECT/$rel").readText())
            if (rel.endsWith("HeatmapShowcaseController.php")) controllerFile = psi
        }
        controller = controllerFile!!
        // Mark vendor/ as Excluded (the IDE "Mark Directory as → Excluded"), which is what the
        // plugin now keys off instead of matching path strings.
        myFixture.findFileInTempDir("vendor")
            ?.let { PsiTestUtil.addExcludedRoot(myFixture.module, it) }
    }

    override fun tearDown() {
        try {
            if (available) {
                myFixture.findFileInTempDir("vendor")
                    ?.let { PsiTestUtil.removeExcludedRoot(myFixture.module, it) }
            }
        } finally {
            super.tearDown()
        }
    }

    private fun traversal(): CallGraphTraversal {
        val settings = HotPathSettings.getInstance().state
        return CallGraphTraversal(project, settings, project.getService(MethodSummaryService::class.java), PhpLanguageSupport)
    }

    private fun callIn(methodName: String, calleeName: String): FunctionReference {
        val method = PsiTreeUtil.findChildrenOfType(controller, Method::class.java)
            .first { it.name == methodName }
        return PsiTreeUtil.findChildrenOfType(method, FunctionReference::class.java)
            .first { it.name == calleeName }
    }

    private fun severityOf(methodName: String, calleeName: String): Severity? =
        traversal().analyzeCallSite(callIn(methodName, calleeName), Long.MAX_VALUE)?.severity

    fun `test cheap delegate is not highlighted`() {
        if (!available) return
        assertEquals(Severity.NONE, severityOf("expectNone_cheapDelegate", "applyTax"))
    }

    fun `test small service chain is low`() {
        if (!available) return
        assertEquals(Severity.LOW, severityOf("expectLow_smallServiceChain", "calculateTotal"))
    }

    fun `test single repository read is medium`() {
        if (!available) return
        assertEquals(Severity.MEDIUM, severityOf("expectMedium_singleRepositoryRead", "findById"))
    }

    fun `test repository scan service is high`() {
        if (!available) return
        assertEquals(Severity.HIGH, severityOf("expectHigh_repositoryScanService", "aggregateAllOrders"))
    }

    fun `test expensive service in loop is very high`() {
        if (!available) return
        assertEquals(Severity.VERY_HIGH, severityOf("expectVeryHigh_expensiveServiceInLoop", "generateMonthlyReport"))
    }

    fun `test call into an Excluded folder is skipped`() {
        if (!available) return
        // vendor/ is marked Excluded in setUp -> target is skipped -> nothing to highlight.
        assertNull(severityOf("expectNone_vendorCallExcluded", "findEverythingExpensively"))
    }
}
