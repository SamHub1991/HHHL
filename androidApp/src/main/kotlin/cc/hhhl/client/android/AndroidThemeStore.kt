package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.theme.HhhlCustomTheme
import cc.hhhl.client.theme.ThemeStore

class AndroidThemeStore(context: Context) : ThemeStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun loadThemePresetName(): String? {
        return preferences.getString(KEY_THEME_PRESET, null)
    }

    override fun saveThemePresetName(presetName: String) {
        preferences.edit()
            .putString(KEY_THEME_PRESET, presetName)
            .apply()
    }

    override fun loadCustomTheme(): HhhlCustomTheme {
        return HhhlCustomTheme(
            accentColorHex = preferences.getString(KEY_CUSTOM_ACCENT_COLOR, null).orEmpty(),
            accentSoftColorHex = preferences.getString(KEY_CUSTOM_ACCENT_SOFT_COLOR, null).orEmpty(),
            backgroundColorHex = preferences.getString(KEY_CUSTOM_BACKGROUND_COLOR, null).orEmpty(),
            surfaceColorHex = preferences.getString(KEY_CUSTOM_SURFACE_COLOR, null).orEmpty(),
            elevatedSurfaceColorHex = preferences.getString(KEY_CUSTOM_ELEVATED_SURFACE_COLOR, null).orEmpty(),
            panelBackgroundColorHex = preferences.getString(KEY_CUSTOM_PANEL_BACKGROUND_COLOR, null).orEmpty(),
            chatBackgroundColorHex = preferences.getString(KEY_CUSTOM_CHAT_BACKGROUND_COLOR, null).orEmpty(),
            inputBackgroundColorHex = preferences.getString(KEY_CUSTOM_INPUT_BACKGROUND_COLOR, null).orEmpty(),
            cardBackgroundColorHex = preferences.getString(KEY_CUSTOM_CARD_BACKGROUND_COLOR, null).orEmpty(),
            noteBackgroundColorHex = preferences.getString(KEY_CUSTOM_NOTE_BACKGROUND_COLOR, null).orEmpty(),
            primaryTextColorHex = preferences.getString(KEY_CUSTOM_PRIMARY_TEXT_COLOR, null).orEmpty(),
            secondaryTextColorHex = preferences.getString(KEY_CUSTOM_SECONDARY_TEXT_COLOR, null).orEmpty(),
            mutedTextColorHex = preferences.getString(KEY_CUSTOM_MUTED_TEXT_COLOR, null).orEmpty(),
            dividerColorHex = preferences.getString(KEY_CUSTOM_DIVIDER_COLOR, null).orEmpty(),
            borderColorHex = preferences.getString(KEY_CUSTOM_BORDER_COLOR, null).orEmpty(),
            mediaBackgroundColorHex = preferences.getString(KEY_CUSTOM_MEDIA_BACKGROUND_COLOR, null).orEmpty(),
            avatarBackgroundColorHex = preferences.getString(KEY_CUSTOM_AVATAR_BACKGROUND_COLOR, null).orEmpty(),
            badgeBackgroundColorHex = preferences.getString(KEY_CUSTOM_BADGE_BACKGROUND_COLOR, null).orEmpty(),
            unreadBadgeColorHex = preferences.getString(KEY_CUSTOM_UNREAD_BADGE_COLOR, null).orEmpty(),
            successColorHex = preferences.getString(KEY_CUSTOM_SUCCESS_COLOR, null).orEmpty(),
            warningColorHex = preferences.getString(KEY_CUSTOM_WARNING_COLOR, null).orEmpty(),
            dangerColorHex = preferences.getString(KEY_CUSTOM_DANGER_COLOR, null).orEmpty(),
            dangerTextColorHex = preferences.getString(KEY_CUSTOM_DANGER_TEXT_COLOR, null).orEmpty(),
            textInverseColorHex = preferences.getString(KEY_CUSTOM_TEXT_INVERSE_COLOR, null).orEmpty(),
            focusRingColorHex = preferences.getString(KEY_CUSTOM_FOCUS_RING_COLOR, null).orEmpty(),
            inputBorderColorHex = preferences.getString(KEY_CUSTOM_INPUT_BORDER_COLOR, null).orEmpty(),
            inputFocusedBorderColorHex = preferences.getString(KEY_CUSTOM_INPUT_FOCUSED_BORDER_COLOR, null).orEmpty(),
            toastBackgroundColorHex = preferences.getString(KEY_CUSTOM_TOAST_BACKGROUND_COLOR, null).orEmpty(),
            toastTextColorHex = preferences.getString(KEY_CUSTOM_TOAST_TEXT_COLOR, null).orEmpty(),
            rankBronzeColorHex = preferences.getString(KEY_CUSTOM_RANK_BRONZE_COLOR, null).orEmpty(),
            rankSilverColorHex = preferences.getString(KEY_CUSTOM_RANK_SILVER_COLOR, null).orEmpty(),
            rankGoldColorHex = preferences.getString(KEY_CUSTOM_RANK_GOLD_COLOR, null).orEmpty(),
            rankPlatinumColorHex = preferences.getString(KEY_CUSTOM_RANK_PLATINUM_COLOR, null).orEmpty(),
            buttonBackgroundColorHex = preferences.getString(KEY_CUSTOM_BUTTON_BACKGROUND_COLOR, null).orEmpty(),
            buttonSelectedBackgroundColorHex = preferences.getString(KEY_CUSTOM_BUTTON_SELECTED_BACKGROUND_COLOR, null).orEmpty(),
            chipBackgroundColorHex = preferences.getString(KEY_CUSTOM_CHIP_BACKGROUND_COLOR, null).orEmpty(),
            chipSelectedBackgroundColorHex = preferences.getString(KEY_CUSTOM_CHIP_SELECTED_BACKGROUND_COLOR, null).orEmpty(),
            topBarBackgroundColorHex = preferences.getString(KEY_CUSTOM_TOP_BAR_BACKGROUND_COLOR, null).orEmpty(),
            bottomNavBackgroundColorHex = preferences.getString(KEY_CUSTOM_BOTTOM_NAV_BACKGROUND_COLOR, null).orEmpty(),
            bottomNavSelectedColorHex = preferences.getString(KEY_CUSTOM_BOTTOM_NAV_SELECTED_COLOR, null).orEmpty(),
            incomingBubbleColorHex = preferences.getString(KEY_CUSTOM_INCOMING_BUBBLE_COLOR, null).orEmpty(),
            outgoingBubbleColorHex = preferences.getString(KEY_CUSTOM_OUTGOING_BUBBLE_COLOR, null).orEmpty(),
            incomingBubbleTextColorHex = preferences.getString(KEY_CUSTOM_INCOMING_BUBBLE_TEXT_COLOR, null).orEmpty(),
            outgoingBubbleTextColorHex = preferences.getString(KEY_CUSTOM_OUTGOING_BUBBLE_TEXT_COLOR, null).orEmpty(),
            chatBubbleBorderColorHex = preferences.getString(KEY_CUSTOM_CHAT_BUBBLE_BORDER_COLOR, null).orEmpty(),
            chatComposerBackgroundColorHex = preferences.getString(KEY_CUSTOM_CHAT_COMPOSER_BACKGROUND_COLOR, null).orEmpty(),
            chatMentionHighlightColorHex = preferences.getString(KEY_CUSTOM_CHAT_MENTION_HIGHLIGHT_COLOR, null).orEmpty(),
            noteActionBackgroundColorHex = preferences.getString(KEY_CUSTOM_NOTE_ACTION_BACKGROUND_COLOR, null).orEmpty(),
            noteReactionBackgroundColorHex = preferences.getString(KEY_CUSTOM_NOTE_REACTION_BACKGROUND_COLOR, null).orEmpty(),
            noteTreeLineColorHex = preferences.getString(KEY_CUSTOM_NOTE_TREE_LINE_COLOR, null).orEmpty(),
            quoteBackgroundColorHex = preferences.getString(KEY_CUSTOM_QUOTE_BACKGROUND_COLOR, null).orEmpty(),
            overlayScrimColorHex = preferences.getString(KEY_CUSTOM_OVERLAY_SCRIM_COLOR, null).orEmpty(),
            shadowColorHex = preferences.getString(KEY_CUSTOM_SHADOW_COLOR, null).orEmpty(),
            globalBackgroundImageDataUri = preferences.getString(KEY_CUSTOM_GLOBAL_BACKGROUND_IMAGE, null).orEmpty(),
            chatBackgroundImageDataUri = preferences.getString(KEY_CUSTOM_CHAT_BACKGROUND_IMAGE, null).orEmpty(),
        )
    }

    override fun saveCustomTheme(customTheme: HhhlCustomTheme) {
        preferences.edit()
            .putString(KEY_CUSTOM_ACCENT_COLOR, customTheme.accentColorHex)
            .putString(KEY_CUSTOM_ACCENT_SOFT_COLOR, customTheme.accentSoftColorHex)
            .putString(KEY_CUSTOM_BACKGROUND_COLOR, customTheme.backgroundColorHex)
            .putString(KEY_CUSTOM_SURFACE_COLOR, customTheme.surfaceColorHex)
            .putString(KEY_CUSTOM_ELEVATED_SURFACE_COLOR, customTheme.elevatedSurfaceColorHex)
            .putString(KEY_CUSTOM_PANEL_BACKGROUND_COLOR, customTheme.panelBackgroundColorHex)
            .putString(KEY_CUSTOM_CHAT_BACKGROUND_COLOR, customTheme.chatBackgroundColorHex)
            .putString(KEY_CUSTOM_INPUT_BACKGROUND_COLOR, customTheme.inputBackgroundColorHex)
            .putString(KEY_CUSTOM_CARD_BACKGROUND_COLOR, customTheme.cardBackgroundColorHex)
            .putString(KEY_CUSTOM_NOTE_BACKGROUND_COLOR, customTheme.noteBackgroundColorHex)
            .putString(KEY_CUSTOM_PRIMARY_TEXT_COLOR, customTheme.primaryTextColorHex)
            .putString(KEY_CUSTOM_SECONDARY_TEXT_COLOR, customTheme.secondaryTextColorHex)
            .putString(KEY_CUSTOM_MUTED_TEXT_COLOR, customTheme.mutedTextColorHex)
            .putString(KEY_CUSTOM_DIVIDER_COLOR, customTheme.dividerColorHex)
            .putString(KEY_CUSTOM_BORDER_COLOR, customTheme.borderColorHex)
            .putString(KEY_CUSTOM_MEDIA_BACKGROUND_COLOR, customTheme.mediaBackgroundColorHex)
            .putString(KEY_CUSTOM_AVATAR_BACKGROUND_COLOR, customTheme.avatarBackgroundColorHex)
            .putString(KEY_CUSTOM_BADGE_BACKGROUND_COLOR, customTheme.badgeBackgroundColorHex)
            .putString(KEY_CUSTOM_UNREAD_BADGE_COLOR, customTheme.unreadBadgeColorHex)
            .putString(KEY_CUSTOM_SUCCESS_COLOR, customTheme.successColorHex)
            .putString(KEY_CUSTOM_WARNING_COLOR, customTheme.warningColorHex)
            .putString(KEY_CUSTOM_DANGER_COLOR, customTheme.dangerColorHex)
            .putString(KEY_CUSTOM_DANGER_TEXT_COLOR, customTheme.dangerTextColorHex)
            .putString(KEY_CUSTOM_TEXT_INVERSE_COLOR, customTheme.textInverseColorHex)
            .putString(KEY_CUSTOM_FOCUS_RING_COLOR, customTheme.focusRingColorHex)
            .putString(KEY_CUSTOM_INPUT_BORDER_COLOR, customTheme.inputBorderColorHex)
            .putString(KEY_CUSTOM_INPUT_FOCUSED_BORDER_COLOR, customTheme.inputFocusedBorderColorHex)
            .putString(KEY_CUSTOM_TOAST_BACKGROUND_COLOR, customTheme.toastBackgroundColorHex)
            .putString(KEY_CUSTOM_TOAST_TEXT_COLOR, customTheme.toastTextColorHex)
            .putString(KEY_CUSTOM_RANK_BRONZE_COLOR, customTheme.rankBronzeColorHex)
            .putString(KEY_CUSTOM_RANK_SILVER_COLOR, customTheme.rankSilverColorHex)
            .putString(KEY_CUSTOM_RANK_GOLD_COLOR, customTheme.rankGoldColorHex)
            .putString(KEY_CUSTOM_RANK_PLATINUM_COLOR, customTheme.rankPlatinumColorHex)
            .putString(KEY_CUSTOM_BUTTON_BACKGROUND_COLOR, customTheme.buttonBackgroundColorHex)
            .putString(KEY_CUSTOM_BUTTON_SELECTED_BACKGROUND_COLOR, customTheme.buttonSelectedBackgroundColorHex)
            .putString(KEY_CUSTOM_CHIP_BACKGROUND_COLOR, customTheme.chipBackgroundColorHex)
            .putString(KEY_CUSTOM_CHIP_SELECTED_BACKGROUND_COLOR, customTheme.chipSelectedBackgroundColorHex)
            .putString(KEY_CUSTOM_TOP_BAR_BACKGROUND_COLOR, customTheme.topBarBackgroundColorHex)
            .putString(KEY_CUSTOM_BOTTOM_NAV_BACKGROUND_COLOR, customTheme.bottomNavBackgroundColorHex)
            .putString(KEY_CUSTOM_BOTTOM_NAV_SELECTED_COLOR, customTheme.bottomNavSelectedColorHex)
            .putString(KEY_CUSTOM_INCOMING_BUBBLE_COLOR, customTheme.incomingBubbleColorHex)
            .putString(KEY_CUSTOM_OUTGOING_BUBBLE_COLOR, customTheme.outgoingBubbleColorHex)
            .putString(KEY_CUSTOM_INCOMING_BUBBLE_TEXT_COLOR, customTheme.incomingBubbleTextColorHex)
            .putString(KEY_CUSTOM_OUTGOING_BUBBLE_TEXT_COLOR, customTheme.outgoingBubbleTextColorHex)
            .putString(KEY_CUSTOM_CHAT_BUBBLE_BORDER_COLOR, customTheme.chatBubbleBorderColorHex)
            .putString(KEY_CUSTOM_CHAT_COMPOSER_BACKGROUND_COLOR, customTheme.chatComposerBackgroundColorHex)
            .putString(KEY_CUSTOM_CHAT_MENTION_HIGHLIGHT_COLOR, customTheme.chatMentionHighlightColorHex)
            .putString(KEY_CUSTOM_NOTE_ACTION_BACKGROUND_COLOR, customTheme.noteActionBackgroundColorHex)
            .putString(KEY_CUSTOM_NOTE_REACTION_BACKGROUND_COLOR, customTheme.noteReactionBackgroundColorHex)
            .putString(KEY_CUSTOM_NOTE_TREE_LINE_COLOR, customTheme.noteTreeLineColorHex)
            .putString(KEY_CUSTOM_QUOTE_BACKGROUND_COLOR, customTheme.quoteBackgroundColorHex)
            .putString(KEY_CUSTOM_OVERLAY_SCRIM_COLOR, customTheme.overlayScrimColorHex)
            .putString(KEY_CUSTOM_SHADOW_COLOR, customTheme.shadowColorHex)
            .putString(KEY_CUSTOM_GLOBAL_BACKGROUND_IMAGE, customTheme.globalBackgroundImageDataUri)
            .putString(KEY_CUSTOM_CHAT_BACKGROUND_IMAGE, customTheme.chatBackgroundImageDataUri)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_theme"
        const val KEY_THEME_PRESET = "theme_preset"
        const val KEY_CUSTOM_ACCENT_COLOR = "custom_accent_color"
        const val KEY_CUSTOM_ACCENT_SOFT_COLOR = "custom_accent_soft_color"
        const val KEY_CUSTOM_BACKGROUND_COLOR = "custom_background_color"
        const val KEY_CUSTOM_SURFACE_COLOR = "custom_surface_color"
        const val KEY_CUSTOM_ELEVATED_SURFACE_COLOR = "custom_elevated_surface_color"
        const val KEY_CUSTOM_PANEL_BACKGROUND_COLOR = "custom_panel_background_color"
        const val KEY_CUSTOM_CHAT_BACKGROUND_COLOR = "custom_chat_background_color"
        const val KEY_CUSTOM_INPUT_BACKGROUND_COLOR = "custom_input_background_color"
        const val KEY_CUSTOM_CARD_BACKGROUND_COLOR = "custom_card_background_color"
        const val KEY_CUSTOM_NOTE_BACKGROUND_COLOR = "custom_note_background_color"
        const val KEY_CUSTOM_PRIMARY_TEXT_COLOR = "custom_primary_text_color"
        const val KEY_CUSTOM_SECONDARY_TEXT_COLOR = "custom_secondary_text_color"
        const val KEY_CUSTOM_MUTED_TEXT_COLOR = "custom_muted_text_color"
        const val KEY_CUSTOM_DIVIDER_COLOR = "custom_divider_color"
        const val KEY_CUSTOM_BORDER_COLOR = "custom_border_color"
        const val KEY_CUSTOM_MEDIA_BACKGROUND_COLOR = "custom_media_background_color"
        const val KEY_CUSTOM_AVATAR_BACKGROUND_COLOR = "custom_avatar_background_color"
        const val KEY_CUSTOM_BADGE_BACKGROUND_COLOR = "custom_badge_background_color"
        const val KEY_CUSTOM_UNREAD_BADGE_COLOR = "custom_unread_badge_color"
        const val KEY_CUSTOM_SUCCESS_COLOR = "custom_success_color"
        const val KEY_CUSTOM_WARNING_COLOR = "custom_warning_color"
        const val KEY_CUSTOM_DANGER_COLOR = "custom_danger_color"
        const val KEY_CUSTOM_DANGER_TEXT_COLOR = "custom_danger_text_color"
        const val KEY_CUSTOM_TEXT_INVERSE_COLOR = "custom_text_inverse_color"
        const val KEY_CUSTOM_FOCUS_RING_COLOR = "custom_focus_ring_color"
        const val KEY_CUSTOM_INPUT_BORDER_COLOR = "custom_input_border_color"
        const val KEY_CUSTOM_INPUT_FOCUSED_BORDER_COLOR = "custom_input_focused_border_color"
        const val KEY_CUSTOM_TOAST_BACKGROUND_COLOR = "custom_toast_background_color"
        const val KEY_CUSTOM_TOAST_TEXT_COLOR = "custom_toast_text_color"
        const val KEY_CUSTOM_RANK_BRONZE_COLOR = "custom_rank_bronze_color"
        const val KEY_CUSTOM_RANK_SILVER_COLOR = "custom_rank_silver_color"
        const val KEY_CUSTOM_RANK_GOLD_COLOR = "custom_rank_gold_color"
        const val KEY_CUSTOM_RANK_PLATINUM_COLOR = "custom_rank_platinum_color"
        const val KEY_CUSTOM_BUTTON_BACKGROUND_COLOR = "custom_button_background_color"
        const val KEY_CUSTOM_BUTTON_SELECTED_BACKGROUND_COLOR = "custom_button_selected_background_color"
        const val KEY_CUSTOM_CHIP_BACKGROUND_COLOR = "custom_chip_background_color"
        const val KEY_CUSTOM_CHIP_SELECTED_BACKGROUND_COLOR = "custom_chip_selected_background_color"
        const val KEY_CUSTOM_TOP_BAR_BACKGROUND_COLOR = "custom_top_bar_background_color"
        const val KEY_CUSTOM_BOTTOM_NAV_BACKGROUND_COLOR = "custom_bottom_nav_background_color"
        const val KEY_CUSTOM_BOTTOM_NAV_SELECTED_COLOR = "custom_bottom_nav_selected_color"
        const val KEY_CUSTOM_INCOMING_BUBBLE_COLOR = "custom_incoming_bubble_color"
        const val KEY_CUSTOM_OUTGOING_BUBBLE_COLOR = "custom_outgoing_bubble_color"
        const val KEY_CUSTOM_INCOMING_BUBBLE_TEXT_COLOR = "custom_incoming_bubble_text_color"
        const val KEY_CUSTOM_OUTGOING_BUBBLE_TEXT_COLOR = "custom_outgoing_bubble_text_color"
        const val KEY_CUSTOM_CHAT_BUBBLE_BORDER_COLOR = "custom_chat_bubble_border_color"
        const val KEY_CUSTOM_CHAT_COMPOSER_BACKGROUND_COLOR = "custom_chat_composer_background_color"
        const val KEY_CUSTOM_CHAT_MENTION_HIGHLIGHT_COLOR = "custom_chat_mention_highlight_color"
        const val KEY_CUSTOM_NOTE_ACTION_BACKGROUND_COLOR = "custom_note_action_background_color"
        const val KEY_CUSTOM_NOTE_REACTION_BACKGROUND_COLOR = "custom_note_reaction_background_color"
        const val KEY_CUSTOM_NOTE_TREE_LINE_COLOR = "custom_note_tree_line_color"
        const val KEY_CUSTOM_QUOTE_BACKGROUND_COLOR = "custom_quote_background_color"
        const val KEY_CUSTOM_OVERLAY_SCRIM_COLOR = "custom_overlay_scrim_color"
        const val KEY_CUSTOM_SHADOW_COLOR = "custom_shadow_color"
        const val KEY_CUSTOM_GLOBAL_BACKGROUND_IMAGE = "custom_global_background_image"
        const val KEY_CUSTOM_CHAT_BACKGROUND_IMAGE = "custom_chat_background_image"
    }
}
