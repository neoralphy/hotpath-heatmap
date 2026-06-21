package com.hotpath.heatmap.analysis

import com.hotpath.heatmap.model.MethodSummary
import com.intellij.openapi.components.Service
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/**
 * Caches the *local* [MethodSummary] for each callable, keyed on the callable PSI element via
 * [CachedValuesManager] and invalidated by [PsiModificationTracker.MODIFICATION_COUNT], so it is
 * recomputed only when PSI changes. The actual (language-specific) computation is delegated to the
 * [LanguageSupport]; this service is only the cache. Must be called inside a read action.
 */
@Service(Service.Level.PROJECT)
class MethodSummaryService {

    fun summaryFor(callable: PsiElement, support: LanguageSupport): MethodSummary =
        CachedValuesManager.getCachedValue(callable) {
            CachedValueProvider.Result.create(
                support.computeSummary(callable),
                PsiModificationTracker.MODIFICATION_COUNT,
            )
        }
}
