package com.example.websitereader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import com.example.websitereader.R

@Composable
fun WebsiteReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colorResource(R.color.md_theme_primary),
            onPrimary = colorResource(R.color.md_theme_onPrimary),
            primaryContainer = colorResource(R.color.md_theme_primaryContainer),
            onPrimaryContainer = colorResource(R.color.md_theme_onPrimaryContainer),
            secondary = colorResource(R.color.md_theme_secondary),
            onSecondary = colorResource(R.color.md_theme_onSecondary),
            secondaryContainer = colorResource(R.color.md_theme_secondaryContainer),
            onSecondaryContainer = colorResource(R.color.md_theme_onSecondaryContainer),
            tertiary = colorResource(R.color.md_theme_tertiary),
            onTertiary = colorResource(R.color.md_theme_onTertiary),
            tertiaryContainer = colorResource(R.color.md_theme_tertiaryContainer),
            onTertiaryContainer = colorResource(R.color.md_theme_onTertiaryContainer),
            error = colorResource(R.color.md_theme_error),
            onError = colorResource(R.color.md_theme_onError),
            errorContainer = colorResource(R.color.md_theme_errorContainer),
            onErrorContainer = colorResource(R.color.md_theme_onErrorContainer),
            background = colorResource(R.color.md_theme_background),
            onBackground = colorResource(R.color.md_theme_onBackground),
            surface = colorResource(R.color.md_theme_surface),
            onSurface = colorResource(R.color.md_theme_onSurface),
            surfaceVariant = colorResource(R.color.md_theme_surfaceVariant),
            onSurfaceVariant = colorResource(R.color.md_theme_onSurfaceVariant),
            outline = colorResource(R.color.md_theme_outline),
            outlineVariant = colorResource(R.color.md_theme_outlineVariant),
            inverseOnSurface = colorResource(R.color.md_theme_inverseOnSurface),
            inverseSurface = colorResource(R.color.md_theme_inverseSurface),
            inversePrimary = colorResource(R.color.md_theme_inversePrimary),
        )
    } else {
        lightColorScheme(
            primary = colorResource(R.color.md_theme_primary),
            onPrimary = colorResource(R.color.md_theme_onPrimary),
            primaryContainer = colorResource(R.color.md_theme_primaryContainer),
            onPrimaryContainer = colorResource(R.color.md_theme_onPrimaryContainer),
            secondary = colorResource(R.color.md_theme_secondary),
            onSecondary = colorResource(R.color.md_theme_onSecondary),
            secondaryContainer = colorResource(R.color.md_theme_secondaryContainer),
            onSecondaryContainer = colorResource(R.color.md_theme_onSecondaryContainer),
            tertiary = colorResource(R.color.md_theme_tertiary),
            onTertiary = colorResource(R.color.md_theme_onTertiary),
            tertiaryContainer = colorResource(R.color.md_theme_tertiaryContainer),
            onTertiaryContainer = colorResource(R.color.md_theme_onTertiaryContainer),
            error = colorResource(R.color.md_theme_error),
            onError = colorResource(R.color.md_theme_onError),
            errorContainer = colorResource(R.color.md_theme_errorContainer),
            onErrorContainer = colorResource(R.color.md_theme_onErrorContainer),
            background = colorResource(R.color.md_theme_background),
            onBackground = colorResource(R.color.md_theme_onBackground),
            surface = colorResource(R.color.md_theme_surface),
            onSurface = colorResource(R.color.md_theme_onSurface),
            surfaceVariant = colorResource(R.color.md_theme_surfaceVariant),
            onSurfaceVariant = colorResource(R.color.md_theme_onSurfaceVariant),
            outline = colorResource(R.color.md_theme_outline),
            outlineVariant = colorResource(R.color.md_theme_outlineVariant),
            inverseOnSurface = colorResource(R.color.md_theme_inverseOnSurface),
            inverseSurface = colorResource(R.color.md_theme_inverseSurface),
            inversePrimary = colorResource(R.color.md_theme_inversePrimary),
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
