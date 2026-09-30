package com.wood.pair

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wood.pair.ui.preview.SampleState
import com.wood.pair.ui.rules.LocationRulesContent
import com.wood.pair.ui.rules.RuleEditorContent
import com.wood.pair.ui.settings.SettingsContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.material3.SnackbarHostState

/**
 * Renders the Settings and location-rule screens.
 *
 * These carry the most hand-built components in the app — the avatar grid, the segmented
 * control, the wavy rules, the day chips — so they are the screens where a shared component
 * drifting is most visible.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    application = TestApplication::class,
    qualifiers = "w411dp-h830dp-normal-long-notround-any-420dpi-keyshidden-nonav",
)
class SettingsAndRulesRenderTest : ScreenRenderTest() {

    @Test
    fun settings() {
        ScreenShots.render(compose, "settings") {
            SettingsContent(
                state = SampleState.settings,
                locationStatus = "Granted",
                batteryUnrestricted = true,
                notificationsEnabled = true,
                canPromote = true,
                liveUpdatesSupported = true,
                onStartEditing = {},
                onDraftChange = {},
                onCommitRename = {},
                onCancelRename = {},
                onSelectAvatar = {},
                onSelectTheme = {},
                onDynamicColorChange = {},
                onAmoledChange = {},
                onLiveUpdateChange = {},
                onOpenNotificationSettings = {},
                onOpenBatterySettings = {},
                onOpenLocationSettings = {},
                onLeaveRoom = {},
                onRemovePartner = {},
                partnerRemoved = false,
                onPartnerRemovedShown = {},
                onNavigateBack = {},
                onNavigateHome = {},
                onOpenRoom = {},
                onOpenLocationRules = {},
            )
        }
    }

    @Test
    fun locationRules() {
        ScreenShots.render(compose, "location-rules") {
            LocationRulesContent(
                state = SampleState.rules,
                // No loader, so the rows render without a preview rather than each reaching for
                // the filesystem: the list's layout must not depend on a wallpaper existing.
                wallpaperStore = null,
                onAddRule = {},
                onEditRule = {},
                onToggleRule = { _, _ -> },
                onSelectDestination = {},
                onOpenSettings = {},
                snackbarHostState = SnackbarHostState(),
            )
        }
    }

    @Test
    fun ruleEditor() {
        ScreenShots.render(compose, "rule-editor") {
            RuleEditorContent(
                state = SampleState.ruleEditor,
                hasLocationPermission = true,
                wallpaperStore = null,
                onLabelChange = {},
                onUseCurrentLocation = {},
                onOpenBackgroundSettings = {},
                onRadiusChange = {},
                onTimeRestrictionChange = {},
                onStartMinuteChange = {},
                onEndMinuteChange = {},
                onToggleDay = {},
                onPickWallpaper = {},
                onWallpaperTargetChange = {},
                onSave = {},
                onSelectDestination = {},
                onOpenSettings = {},
                snackbarHostState = SnackbarHostState(),
            )
        }
    }
}
