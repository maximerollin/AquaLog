package com.maximerollin.aqualog.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.maximerollin.aqualog.ui.theme.dmSansFamily
import androidx.compose.runtime.remember
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.indication
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.ProMessage
import com.maximerollin.aqualog.ProPurchaseStatus
import com.maximerollin.aqualog.ProUiState
import com.maximerollin.aqualog.currentLocalizedFormatters
import com.maximerollin.aqualog.home.HeroText
import com.maximerollin.aqualog.localizedBillingDate
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.close
import com.maximerollin.aqualog.resources.continue_for_free
import com.maximerollin.aqualog.resources.continue_with_pro
import com.maximerollin.aqualog.resources.google_play_offer_unavailable
import com.maximerollin.aqualog.resources.loading_google_play_offer
import com.maximerollin.aqualog.resources.paywall_annual_terms
import com.maximerollin.aqualog.resources.paywall_annual_terms_no_trial
import com.maximerollin.aqualog.resources.paywall_benefit_analysis
import com.maximerollin.aqualog.resources.paywall_benefit_aquariums
import com.maximerollin.aqualog.resources.paywall_benefit_history
import com.maximerollin.aqualog.resources.paywall_benefit_widgets
import com.maximerollin.aqualog.resources.paywall_choose_pro
import com.maximerollin.aqualog.resources.paywall_description
import com.maximerollin.aqualog.resources.paywall_headline
import com.maximerollin.aqualog.resources.paywall_lifetime_terms
import com.maximerollin.aqualog.resources.paywall_plan_annual
import com.maximerollin.aqualog.resources.paywall_plan_lifetime
import com.maximerollin.aqualog.resources.paywall_pro_overline
import com.maximerollin.aqualog.resources.paywall_recommended
import com.maximerollin.aqualog.resources.pro_lifetime_offer_summary
import com.maximerollin.aqualog.resources.pro_message_active
import com.maximerollin.aqualog.resources.pro_message_authentication_required
import com.maximerollin.aqualog.resources.pro_message_play_unavailable
import com.maximerollin.aqualog.resources.pro_message_purchase_cancelled
import com.maximerollin.aqualog.resources.pro_message_purchase_pending
import com.maximerollin.aqualog.resources.pro_message_save_failed
import com.maximerollin.aqualog.resources.pro_message_verification_failed
import com.maximerollin.aqualog.resources.pro_offer_summary
import com.maximerollin.aqualog.resources.pro_offer_summary_no_trial
import com.maximerollin.aqualog.resources.restore_purchase
import com.maximerollin.aqualog.resources.try_again
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.ui.icon.AquagendaIcons
import com.maximerollin.aqualog.ui.icon.Check
import com.maximerollin.aqualog.ui.theme.LargeFontScale
import com.maximerollin.aqualog.ui.theme.PaperSheetTop
import com.maximerollin.aqualog.ui.theme.frauncesFamily
import com.maximerollin.aqualog.ui.theme.overlapNext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class ProPlan { ANNUAL, LIFETIME }

/** The plans Google Play offers right now: annual first, then lifetime. */
fun availablePlans(pro: ProUiState): List<ProPlan> = buildList {
    if (pro.offer != null) add(ProPlan.ANNUAL)
    if (pro.lifetimeOffer != null) add(ProPlan.LIFETIME)
}

fun initialPlan(pro: ProUiState): ProPlan? = availablePlans(pro).firstOrNull()

/** A purchase is under way or done: starting another one would be wrong. */
fun ProUiState.purchaseBusy(): Boolean = purchaseStatus in setOf(
    ProPurchaseStatus.AWAITING_PLAY_CONFIRMATION,
    ProPurchaseStatus.PENDING,
    ProPurchaseStatus.VERIFYING,
    ProPurchaseStatus.ACTIVE,
)

class PaywallActions(
    val onStartAnnual: () -> Unit,
    val onStartLifetime: () -> Unit,
    val onRestore: () -> Unit,
    val onRetryOffer: () -> Unit,
    val onContinue: () -> Unit,
)

/** The Offer: Free stays the default, Pro is one deliberate choice between the plans Google Play offers. */
@Composable
fun PaywallContent(
    pro: ProUiState,
    isSaving: Boolean,
    onboarding: Boolean,
    aquarium: Aquarium,
    actions: PaywallActions,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val formatter = currentLocalizedFormatters()
    // While the offer reloads the content shows only the spinner: no plan can be chosen.
    val plans = if (pro.isLoadingOffer) emptyList() else availablePlans(pro)
    var stored by rememberSaveable { mutableStateOf(initialPlan(pro)?.name) }
    val selected = plans.firstOrNull { it.name == stored } ?: plans.firstOrNull()
    val continueLabel = stringResource(
        when {
            // Opened from the home, Trends, More or a widget: it closes back to wherever that was.
            !onboarding -> Res.string.close
            pro.purchaseStatus == ProPurchaseStatus.ACTIVE -> Res.string.continue_with_pro
            else -> Res.string.continue_for_free
        },
    )
    val continueAction = actions.onContinue
    // Once Pro is active there is nothing left to choose: Continue becomes the primary.
    val choosing = selected.takeIf { pro.purchaseStatus != ProPurchaseStatus.ACTIVE }
    OnboardingScaffold(
        listState = listState,
        primaryLabel = if (choosing != null) stringResource(Res.string.paywall_choose_pro) else continueLabel,
        onPrimary = when (choosing) {
            ProPlan.ANNUAL -> actions.onStartAnnual
            ProPlan.LIFETIME -> actions.onStartLifetime
            null -> continueAction
        },
        modifier = modifier,
        primaryEnabled = if (choosing != null) !pro.purchaseBusy() else !isSaving,
        primaryBusy = choosing == null && isSaving,
        primaryTag = if (choosing != null) "choose-pro" else "onboarding-primary",
        secondary = if (choosing == null) null else {
            {
                TextButton(
                    onClick = continueAction,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    if (isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(continueLabel, textAlign = TextAlign.Center)
                }
            }
        },
    ) {
        item {
            OnboardingHeader(
                aquarium = aquarium,
                minHeight = 330.dp,
                animate = true,
                modifier = Modifier.overlapNext(30.dp),
                step = if (onboarding) 5 else null,
            ) {
                Text(
                    stringResource(Res.string.paywall_pro_overline).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
                    color = HeroText,
                )
                Text(
                    stringResource(Res.string.paywall_headline),
                    modifier = Modifier.padding(top = 4.dp).semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = frauncesFamily(), fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp,
                    ),
                    color = HeroText,
                )
                Text(
                    stringResource(Res.string.paywall_description),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = HeroText,
                )
            }
        }
        item { PaperSheetTop() }
        item {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listOf(
                    Res.string.paywall_benefit_aquariums,
                    Res.string.paywall_benefit_history,
                    Res.string.paywall_benefit_analysis,
                    Res.string.paywall_benefit_widgets,
                ).forEach { Benefit(it) }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    pro.isLoadingOffer -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(stringResource(Res.string.loading_google_play_offer))
                    }
                    selected == null -> {
                        Text(stringResource(Res.string.google_play_offer_unavailable), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = actions.onRetryOffer, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(Res.string.try_again), textAlign = TextAlign.Center)
                        }
                    }
                    else -> {
                        val cards: List<@Composable (Modifier) -> Unit> = plans.map { plan ->
                            { cardModifier: Modifier ->
                                when (plan) {
                                    ProPlan.ANNUAL -> PlanCard(
                                        title = stringResource(Res.string.paywall_plan_annual),
                                        price = pro.offer!!.formattedPrice,
                                        terms = if (pro.offer.trialDays > 0) {
                                            stringResource(Res.string.paywall_annual_terms, pro.offer.trialDays)
                                        } else {
                                            stringResource(Res.string.paywall_annual_terms_no_trial)
                                        },
                                        badge = stringResource(Res.string.paywall_recommended),
                                        selected = selected == plan,
                                        onClick = { stored = plan.name },
                                        tag = "plan-annual",
                                        modifier = cardModifier,
                                    )
                                    ProPlan.LIFETIME -> PlanCard(
                                        title = stringResource(Res.string.paywall_plan_lifetime),
                                        price = pro.lifetimeOffer!!.formattedPrice,
                                        terms = stringResource(Res.string.paywall_lifetime_terms),
                                        badge = null,
                                        selected = selected == plan,
                                        onClick = { stored = plan.name },
                                        tag = "plan-lifetime",
                                        modifier = cardModifier,
                                    )
                                }
                            }
                        }
                        if (LocalDensity.current.fontScale >= LargeFontScale) {
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                cards.forEach { it(Modifier.fillMaxWidth()) }
                            }
                        } else {
                            Row(
                                Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                cards.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
                            }
                        }
                        Text(
                            when (selected) {
                                ProPlan.ANNUAL -> if (pro.offer!!.trialDays > 0) {
                                    stringResource(
                                        Res.string.pro_offer_summary,
                                        pro.offer.formattedPrice,
                                        pro.offer.trialDays,
                                        pro.offer.localizedBillingDate(formatter),
                                    )
                                } else {
                                    stringResource(Res.string.pro_offer_summary_no_trial, pro.offer.formattedPrice)
                                }
                                ProPlan.LIFETIME -> stringResource(
                                    Res.string.pro_lifetime_offer_summary,
                                    pro.lifetimeOffer!!.formattedPrice,
                                )
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                pro.message?.let { message ->
                    Text(
                        stringResource(message.resourceId()),
                        color = if (pro.purchaseStatus == ProPurchaseStatus.ACTIVE) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                TextButton(
                    onClick = actions.onRestore,
                    enabled = pro.purchaseStatus != ProPurchaseStatus.VERIFYING,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(Res.string.restore_purchase), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun Benefit(text: StringResource) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(24.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AquagendaIcons.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(stringResource(text), fontSize = 15.sp)
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    terms: String,
    badge: String?,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .selectable(
                selected = selected, role = Role.RadioButton, onClick = onClick,
                interactionSource = interaction, indication = null,
            )
            .testTag(tag),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(top = 11.dp)
                .clip(shape).indication(interaction, ripple()),
            shape = shape,
            color = colors.surface,
            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(end = 24.dp))
                Text(
                    price,
                    style = TextStyle(
                        fontFamily = dmSansFamily(), fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                )
                Text(terms, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.onSurfaceVariant)
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 21.dp, end = 10.dp).size(20.dp)
                .background(if (selected) colors.primary else colors.surface, CircleShape)
                .then(if (selected) Modifier else Modifier.border(1.5.dp, colors.outlineVariant, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(AquagendaIcons.Check, contentDescription = null, modifier = Modifier.size(12.dp), tint = colors.onPrimary)
            }
        }
        if (badge != null) {
            Text(
                badge.uppercase(),
                modifier = Modifier.align(Alignment.TopStart).offset(x = 12.dp)
                    .heightIn(min = 22.dp).background(colors.primary, CircleShape).padding(horizontal = 10.dp)
                    .wrapContentHeight(Alignment.CenterVertically),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                color = colors.onPrimary,
            )
        }
    }
}

internal fun ProMessage.resourceId(): StringResource = when (this) {
    ProMessage.PLAY_UNAVAILABLE -> Res.string.pro_message_play_unavailable
    ProMessage.PURCHASE_PENDING -> Res.string.pro_message_purchase_pending
    ProMessage.PURCHASE_CANCELLED -> Res.string.pro_message_purchase_cancelled
    ProMessage.AUTHENTICATION_REQUIRED -> Res.string.pro_message_authentication_required
    ProMessage.ACTIVE -> Res.string.pro_message_active
    ProMessage.VERIFICATION_FAILED -> Res.string.pro_message_verification_failed
    ProMessage.SAVE_FAILED -> Res.string.pro_message_save_failed
}
