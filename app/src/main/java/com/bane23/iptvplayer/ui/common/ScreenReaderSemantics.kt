package com.bane23.iptvplayer.ui.common

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Opis elemenata za usluge pristupačnosti traži se samo kad je uključena usluga koja ga
 * stvarno čita (čitač ekrana, brajev red, istraživanje dodirom).
 *
 * Usluge kao što su zatamnjenje ekrana ili zamenski pokretač ne čitaju opis, ali je samo
 * njihovo postojanje dovoljno da Compose pri svakoj promeni prođe kroz ceo opis ekrana.
 * Na slabijem TV-u to je glavni trošak pri listanju. Tada se opis sklanja sa celog ekrana.
 */
@Composable
fun SemanticsWhenNeeded(content: @Composable () -> Unit) {
    val needed = rememberSemanticsNeeded()
    // Deca dobijaju iste mere kao bez omotača: ceo prozor.
    Box(
        modifier = Modifier.fillMaxSize().then(if (needed) Modifier else Modifier.clearAndSetSemantics {}),
        propagateMinConstraints = true,
    ) { content() }
}

@Composable
private fun rememberSemanticsNeeded(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    var needed by remember(manager) { mutableStateOf(semanticsNeeded(manager)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(manager, lifecycle) {
        val update = { needed = semanticsNeeded(manager) }
        val stateListener = AccessibilityManager.AccessibilityStateChangeListener { update() }
        val touchListener = AccessibilityManager.TouchExplorationStateChangeListener { update() }
        manager?.addAccessibilityStateChangeListener(stateListener)
        manager?.addTouchExplorationStateChangeListener(touchListener)
        // Spisak uključenih usluga nema svoj signal, pa se proverava i pri povratku u aplikaciju.
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) update() }
        lifecycle.addObserver(observer)
        onDispose {
            manager?.removeAccessibilityStateChangeListener(stateListener)
            manager?.removeTouchExplorationStateChangeListener(touchListener)
            lifecycle.removeObserver(observer)
        }
    }
    return needed
}

private const val READING_FEEDBACK =
    AccessibilityServiceInfo.FEEDBACK_SPOKEN or
        AccessibilityServiceInfo.FEEDBACK_BRAILLE or
        AccessibilityServiceInfo.FEEDBACK_AUDIBLE or
        AccessibilityServiceInfo.FEEDBACK_HAPTIC

/** U nedoumici opis ostaje: sklanja se samo kad je sigurno da ga nijedna usluga ne čita. */
private fun semanticsNeeded(manager: AccessibilityManager?): Boolean {
    if (manager == null) return true
    if (!manager.isEnabled) return true
    if (manager.isTouchExplorationEnabled) return true
    val services = runCatching {
        manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    }.getOrNull() ?: return true
    if (services.isEmpty()) return true
    return services.any { it.feedbackType and READING_FEEDBACK != 0 }
}
