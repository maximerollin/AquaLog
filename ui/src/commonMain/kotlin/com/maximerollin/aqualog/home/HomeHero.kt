package com.maximerollin.aqualog.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.HomeOverview
import com.maximerollin.aqualog.SEPARATOR
import com.maximerollin.aqualog.formattedVolume
import com.maximerollin.aqualog.rememberReducedMotion
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.habitants_add_yours
import com.maximerollin.aqualog.resources.habitants_open
import com.maximerollin.aqualog.resources.habitants_title
import com.maximerollin.aqualog.resources.home_change_aquarium
import com.maximerollin.aqualog.resources.home_inhabitants_count
import com.maximerollin.aqualog.resources.home_no_measurement_yet
import com.maximerollin.aqualog.resources.photo_add
import com.maximerollin.aqualog.resources.photo_open
import com.maximerollin.aqualog.resources.profile_cycling
import com.maximerollin.aqualog.resources.profile_established
import com.maximerollin.aqualog.resources.profile_planted_shrimp
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.ui.icon.AquagendaIcons
import com.maximerollin.aqualog.ui.icon.Camera
import com.maximerollin.aqualog.ui.theme.GlassIconButton
import com.maximerollin.aqualog.ui.theme.frauncesFamily
import java.time.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Height of the home's water hero, status bar included; it grows when its content needs more (large fonts). */
private val HeroHeight = 430.dp

/** How much of the hero's bottom the paper sheet covers. */
internal val HeroSheetOverlap = 30.dp

/** The pager dots sit this far above the hero's bottom, 10 dp clear of the sheet. */
internal val HeroDotsBottom = HeroSheetOverlap + 10.dp

/** The band the pager dots take (16 dp row) above [HeroDotsBottom]: the hero's content always stays above it. */
private val HeroDotsBand = 16.dp

/** Text on the water. */
internal val HeroText = Color.White

/**
 * Deep water behind every text block on the hero: white keeps 4.5:1 over the lightest sprite and over a white
 * photo (ScenePhysicsTest). The hero shade alone cannot, since the water under it can be anything.
 */
internal val HeroTextBacking = Color(0xFF0F4A43).copy(alpha = .75f)

/** Darkens the top for the status bar and the name, and the bottom where the sheet lands. */
private val HeroShade = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = .4f),
    .35f to Color.Transparent,
    .7f to Color.Transparent,
    1f to Color.Black.copy(alpha = .3f),
)

private val TextShadow = Shadow(Color.Black.copy(alpha = .45f), Offset(0f, 1f), blurRadius = 3f)

/**
 * The home's water: the tank scene (or the Aquarium's photo) from the top edge, the name block, and the status card.
 * [overview] is null on a pager page that is not the current Aquarium: it has no status card.
 */
@Composable
fun HomeHero(
    aquarium: Aquarium,
    overview: HomeOverview?,
    today: LocalDate,
    inhabitantCount: Int,
    scene: MiniScene,
    still: Boolean,
    photo: ImageBitmap?,
    onChangeAquarium: () -> Unit,
    onOpenPhoto: (() -> Unit)?,
    onOpenHabitants: (() -> Unit)?,
    onAddHabitants: (() -> Unit)?,
    onOpenStatus: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val habitantsLabel = stringResource(Res.string.habitants_title)
    val habitantsOpen = stringResource(Res.string.habitants_open)
    Box(
        // The water opens the habitants sheet; the name block, the camera, the card and the chip keep their own actions.
        modifier.fillMaxWidth().let {
            if (onOpenHabitants == null) {
                it
            } else {
                it.clickable(onClickLabel = habitantsOpen, onClick = onOpenHabitants).semantics { contentDescription = habitantsLabel }
            }
        },
    ) {
        // The water fills whatever height the content takes.
        if (photo != null) {
            Image(photo, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } else {
            TankScene(aquarium, scene.sprites, Modifier.matchParentSize(), scene.plants, animate = !still && !rememberReducedMotion())
        }
        Box(Modifier.matchParentSize().background(HeroShade))
        // At least HeroHeight; with large fonts it grows, and the chip, the dots band and the sheet's overlap stay below the content.
        Column(
            Modifier.fillMaxWidth().heightIn(min = HeroHeight).windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, end = 8.dp, top = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) { NameBlock(aquarium, inhabitantCount, onChangeAquarium) }
                val photoLabel = stringResource(if (aquarium.photoId != null) Res.string.photo_open else Res.string.photo_add, aquarium.name)
                GlassIconButton(AquagendaIcons.Camera, photoLabel, onClick = { onOpenPhoto?.invoke() })
            }
            if (overview != null) {
                Spacer(Modifier.height(16.dp))
                StatusCard(overview, today, onOpenStatus, Modifier.padding(start = 8.dp, end = 12.dp))
            }
            // Pushes what follows to the bottom when the content is shorter than HeroHeight.
            Spacer(Modifier.weight(1f))
            if (inhabitantCount == 0 && onAddHabitants != null) {
                GlassChip(
                    stringResource(Res.string.habitants_add_yours),
                    onAddHabitants,
                    Modifier.align(Alignment.End).padding(top = 8.dp, end = 4.dp),
                )
            }
            Spacer(Modifier.height(HeroDotsBottom + HeroDotsBand))
        }
    }
}

/** « Salon » in Fraunces, then « 120 L · Planted / shrimp · 16 inhabitants »; one click target that changes Aquarium. */
@Composable
private fun NameBlock(aquarium: Aquarium, inhabitantCount: Int, onChangeAquarium: () -> Unit) {
    val profile = stringResource(
        when (aquarium.profile) {
            AquariumProfile.ESTABLISHED -> Res.string.profile_established
            AquariumProfile.CYCLING -> Res.string.profile_cycling
            AquariumProfile.PLANTED_SHRIMP -> Res.string.profile_planted_shrimp
        },
    )
    val inhabitants = inhabitantCount.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.home_inhabitants_count, it, it) }
    Column(
        Modifier.heightIn(min = 48.dp)
            .clickable(onClickLabel = stringResource(Res.string.home_change_aquarium), onClick = onChangeAquarium)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .background(HeroTextBacking, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            aquarium.name,
            style = MaterialTheme.typography.displaySmall.copy(
                fontFamily = frauncesFamily(), fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 38.sp, shadow = TextShadow,
            ),
            color = HeroText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOfNotNull(aquarium.formattedVolume(), profile, inhabitants).joinToString(SEPARATOR),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, shadow = TextShadow),
            color = HeroText,
        )
    }
}

/** Dark glass card: the in-range ring and « 6 of 7 in range », then the status sentence; one node for TalkBack. */
@Composable
private fun StatusCard(overview: HomeOverview, today: LocalDate, onOpen: (() -> Unit)?, modifier: Modifier) {
    val shape = RoundedCornerShape(22.dp)
    val ratio = statusRatio(overview)?.resolve()
    val status = statusText(overview, today)
    Row(
        modifier.testTag("status-card").clip(shape)
            .background(HeroTextBacking)
            .border(1.dp, Color.White.copy(alpha = .25f), shape)
            .let { if (onOpen != null) it.clickable(role = Role.Button, onClick = onOpen) else it.semantics(mergeDescendants = true) {} }
            .heightIn(min = 48.dp)
            .padding(start = 10.dp, top = 10.dp, end = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (ratio != null) InRangeRing(overview.inRangeCount, overview.measuredCount)
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                ratio ?: stringResource(Res.string.home_no_measurement_yet),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = HeroText,
            )
            // Before the first Measurement the summary is the title itself, with no « since »: nothing to add.
            if (ratio != null) {
                val since = status.since?.resolve()
                Text(
                    listOfNotNull(status.summary.resolve(), since).joinToString(SEPARATOR),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = HeroText,
                )
            }
        }
    }
}

/** 46 dp ring filled to [inRange] / [measured], « n/total » in the centre; the card's title says it in words. */
@Composable
private fun InRangeRing(inRange: Int, measured: Int) {
    Box(Modifier.testTag("status-ring").size(46.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val inset = Offset(stroke / 2, stroke / 2)
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(Color.White.copy(alpha = .25f), 0f, 360f, useCenter = false, topLeft = inset, size = arc, style = Stroke(stroke))
            if (measured > 0 && inRange > 0) {
                drawArc(
                    Color.White, -90f, 360f * inRange / measured, useCenter = false, topLeft = inset, size = arc,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        val fraction = "$inRange/$measured" // digits and a slash: the same in every language, like the pager's « 2 / 3 »
        Text(fraction, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = HeroText, maxLines = 1)
    }
}

/** A pill of dark glass on the water, with a 48 dp target. */
@Composable
private fun GlassChip(label: String, onClick: () -> Unit, modifier: Modifier) {
    val pill = RoundedCornerShape(50)
    Box(modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(
            label,
            modifier = Modifier.background(HeroTextBacking, pill).border(1.dp, Color.White.copy(alpha = .3f), pill)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = HeroText,
        )
    }
}

/**
 * Measures the content at full height but reports [overlap] less to the parent, so the next list item,
 * drawn later, covers the content's bottom.
 */
fun Modifier.overlapNext(overlap: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = (placeable.height - overlap.roundToPx()).coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(placeable.width, height) { placeable.place(0, 0) }
}
