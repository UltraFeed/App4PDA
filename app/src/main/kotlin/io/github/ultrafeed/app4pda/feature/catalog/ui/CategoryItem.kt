package io.github.ultrafeed.app4pda.feature.catalog.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.ultrafeed.app4pda.data.forum.ForumSections
import io.github.ultrafeed.app4pda.data.forum.SectionIcon
import io.github.ultrafeed.app4pda.feature.catalog.CatalogCategory

@Composable
internal fun CategoryItem(category: CatalogCategory, onOpen: () -> Unit) {
    val icon = category.icon.materialIcon()
    ListItem(
        onClick = onOpen,
        colors =
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = { Icon(icon, null) },
        content = {
            Text(
                ForumSections.titleResource(category.id)?.let { stringResource(it) }
                    ?: category.name,
            )
        },
    )
}

internal fun SectionIcon.materialIcon(): ImageVector = categoryIcons.getValue(this)

private val categoryIcons by lazy {
    mapOf(
        SectionIcon.APPS to Icons.Rounded.Apps,
        SectionIcon.GAMES to Icons.Rounded.SportsEsports,
        SectionIcon.EMULATORS to Icons.Rounded.SportsEsports,
        SectionIcon.ACTION to Icons.Rounded.SportsEsports,
        SectionIcon.ARCADE to Icons.Rounded.SportsEsports,
        SectionIcon.SECURITY to Icons.Rounded.Shield,
        SectionIcon.CHILDREN to Icons.Rounded.ChildCare,
        SectionIcon.INTERNET to Icons.Rounded.Language,
        SectionIcon.INTERFACE to Icons.Rounded.Dashboard,
        SectionIcon.PHONE to Icons.Rounded.Call,
        SectionIcon.MEDIA to Icons.Rounded.PlayCircle,
        SectionIcon.AUDIO to Icons.Rounded.GraphicEq,
        SectionIcon.NEWS to Icons.Rounded.Newspaper,
        SectionIcon.CHAT to Icons.Rounded.Forum,
        SectionIcon.OFFICE to Icons.Rounded.BusinessCenter,
        SectionIcon.BOOK to Icons.AutoMirrored.Rounded.MenuBook,
        SectionIcon.SYSTEM to Icons.Rounded.Settings,
        SectionIcon.HEALTH to Icons.Rounded.FitnessCenter,
        SectionIcon.SHOP to Icons.Rounded.ShoppingCart,
        SectionIcon.TRAVEL to Icons.Rounded.DirectionsCar,
        SectionIcon.RACING to Icons.Rounded.DirectionsCar,
        SectionIcon.FINANCE to Icons.Rounded.Payments,
        SectionIcon.DEVICES to Icons.Rounded.Devices,
        SectionIcon.ARCHIVE to Icons.Rounded.Archive,
        SectionIcon.COMPUTER to Icons.Rounded.Computer,
        SectionIcon.SIMULATION to Icons.Rounded.Computer,
        SectionIcon.UPCOMING to Icons.Rounded.Schedule,
        SectionIcon.PUZZLE to Icons.Rounded.Extension,
        SectionIcon.BOARD to Icons.Rounded.Casino,
        SectionIcon.CASUAL to Icons.Rounded.Casino,
        SectionIcon.ADVENTURE to Icons.Rounded.Explore,
        SectionIcon.SPORTS to Icons.Rounded.SportsSoccer,
        SectionIcon.STRATEGY to Icons.Rounded.Flag,
        SectionIcon.OTHER to Icons.Rounded.Category,
    )
}
